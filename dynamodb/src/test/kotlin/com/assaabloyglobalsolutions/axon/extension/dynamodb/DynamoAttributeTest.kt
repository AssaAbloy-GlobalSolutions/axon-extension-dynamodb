package com.assaabloyglobalsolutions.axon.extension.dynamodb

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.*

class DynamoAttributeTest : DynamoTest() {

    private val tableName = "attribute-test"

    @BeforeEach
    fun init() {
        client.createTable {
            it.tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    AttributeDefinition.builder().attributeName("hash").attributeType(
                        ScalarAttributeType.S
                    ).build()
                )
                .keySchema(
                    KeySchemaElement.builder().attributeName("hash").keyType(KeyType.HASH).build(),
                )
        }

        client.waiter().waitUntilTableExists { it.tableName(tableName) }
    }

    @AfterEach
    fun shutdown() {
        client.deleteTable { it.tableName(tableName) }
        client.waiter().waitUntilTableNotExists { it.tableName(tableName) }
    }

    @Test
    fun `empty set`() {
        val stringSet = DynamoAttribute.StringSet("emptytest")
        val key = "hash" to AttributeValue.fromS(uuid())

        client.putItem { it.tableName(tableName).item(mapOf(key, stringSet.valuePair(setOf()))) }

        val strings = client.getItem { it.tableName(tableName).key(mapOf(key)) }.item().let { stringSet.from(it) }

        assertThat(strings).isEmpty()

        client.updateItem {
            it.tableName(tableName)
                .key(mapOf(key))
                .updateExpression("add $stringSet :toAdd")
                .expressionAttributeValues(
                    mapOf(
                        ":toAdd" to AttributeValue.fromSs(listOf("three"))
                    )
                )
        }

        val added = client.getItem { it.tableName(tableName).key(mapOf(key)) }.item().let { stringSet.from(it) }

        assertThat(added).containsOnly("three")

        client.updateItem {
            it.tableName(tableName)
                .key(mapOf(key))
                .updateExpression("delete $stringSet :toRemove")
                .expressionAttributeValues(
                    mapOf(
                        ":toRemove" to AttributeValue.fromSs(listOf("three"))
                    )
                )
        }

        val removed = client.getItem { it.tableName(tableName).key(mapOf(key)) }.item().let { stringSet.from(it) }

        assertThat(removed).isEmpty()
    }

    @Test
    fun `set add and remove from string set`() {
        val stringSet = DynamoAttribute.StringSet("test")
        val key = "hash" to AttributeValue.fromS(uuid())

        client.putItem { it.tableName(tableName).item(mapOf(key, stringSet.valuePair(setOf("one", "two")))) }

        val strings = client.getItem { it.tableName(tableName).key(mapOf(key)) }.item().let { stringSet.from(it) }

        assertThat(strings).containsOnly("one", "two")

        client.updateItem {
            it.tableName(tableName)
                .key(mapOf(key))
                .updateExpression("add $stringSet :toAdd")
                .expressionAttributeValues(
                    mapOf(
                        ":toAdd" to AttributeValue.fromSs(listOf("three"))
                    )
                )
        }

        val added = client.getItem { it.tableName(tableName).key(mapOf(key)) }.item().let { stringSet.from(it) }

        assertThat(added).containsOnly("one", "two", "three")

        client.updateItem {
            it.tableName(tableName)
                .key(mapOf(key))
                .updateExpression("delete $stringSet :toRemove")
                .expressionAttributeValues(
                    mapOf(
                        ":toRemove" to AttributeValue.fromSs(listOf("one"))
                    )
                )
        }

        val removed = client.getItem { it.tableName(tableName).key(mapOf(key)) }.item().let { stringSet.from(it) }

        assertThat(removed).containsOnly("two", "three")
    }
}