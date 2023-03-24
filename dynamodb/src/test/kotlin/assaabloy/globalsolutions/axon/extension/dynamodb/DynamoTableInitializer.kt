package assaabloy.globalsolutions.axon.extension.dynamodb

import mu.KotlinLogging
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

class DynamoTableInitializer {
    companion object {
        private val logger = KotlinLogging.logger { }

        const val AXON_STORAGE = "axon_storage"
        const val EVENTS_GSI_NAME = "events_gsi"

        fun initTables(client: DynamoDbClient) {
            val existing = client.listTables().tableNames()

            if (AXON_STORAGE !in existing) {
                createEventsTable(client)
            }

            listOf(AXON_STORAGE).forEach { name ->
                client.waiter().waitUntilTableExists { it.tableName(name) }
            }
        }

        private fun createEventsTable(client: DynamoDbClient) {
            logger.info { "Creating required table: events" }

            client.createTable {
                it.tableName(AXON_STORAGE)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("gsh").attributeType(
                            ScalarAttributeType.N
                        ).build(),
                        AttributeDefinition.builder().attributeName("gsl").attributeType(
                            ScalarAttributeType.N
                        ).build(),
                        AttributeDefinition.builder().attributeName("hk")
                            .attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(
                            ScalarAttributeType.N
                        ).build()
                    )
                    .keySchema(
                        KeySchemaElement.builder().attributeName("hk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()
                    )
                    .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                            .indexName(EVENTS_GSI_NAME)
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .keySchema(
                                KeySchemaElement.builder().attributeName("gsh").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("gsl").keyType(KeyType.RANGE).build()
                            )
                            .build()
                    )
            }
        }

        fun dropTables(client: DynamoDbClient) {
            logger.info { "Dropping event store tables" }
            val existing = client.listTables().tableNames()

            val names = listOf(
                AXON_STORAGE
            ).filter {
                it in existing
            }

            names.forEach { name -> client.deleteTable { it.tableName(name) } }
            names.forEach { name -> client.waiter().waitUntilTableNotExists { it.tableName(name) } }
        }
    }
}