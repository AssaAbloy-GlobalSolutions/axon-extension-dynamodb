package assaabloy.globalsolutions.axon.extension.dynamodb.springboot.autoconfig

import assaabloy.globalsolutions.axon.extension.dynamodb.DynamoStorageEngine
import assaabloy.globalsolutions.axon.extension.dynamodb.DynamoTokenStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.axonframework.common.jpa.EntityManagerProvider
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.Configuration
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.serialization.Serializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.*

@AutoConfiguration
@EnableConfigurationProperties(AxonDynamoProperties::class)
@AutoConfigureAfter(name = [
    "org.axonframework.springboot.autoconfig.TransactionAutoConfiguration",
    "org.axonframework.springboot.autoconfig.JdbcAutoConfiguration"
])
open class DynamoDbAutoConfiguration(private val axonDynamoProperties: AxonDynamoProperties) {

    @Bean
    @ConditionalOnMissingBean
    open fun eventStorageEngine(
        defaultSerializer: Serializer?,
        @Qualifier("eventSerializer") eventSerializer: Serializer?,
        configuration: Configuration,
        entityManagerProvider: EntityManagerProvider?,
        transactionManager: TransactionManager?,
        dynamoClient: DynamoDbClient,
        objectMapper: ObjectMapper,
    ): EventStorageEngine {
        return DynamoStorageEngine.builder()
            .dynamoClient(dynamoClient)
            .jacksonObjectMapper(objectMapper)
            .tableName(axonDynamoProperties.axonStorageTableName!!)
            .eventPayloadPackagePrefix(axonDynamoProperties.eventPayloadPackagePrefix)
            .indexName("events_gsi")
            .snapshotSerializer(defaultSerializer)
            .upcasterChain(configuration.upcasterChain())
            .eventSerializer(eventSerializer)
            .snapshotFilter(configuration.snapshotFilter())
            .build()
    }

    @Bean
    @ConditionalOnMissingBean
    open fun tokenStore(
        serializer: Serializer,
        dynamoClient: DynamoDbClient
    ): TokenStore {
        return DynamoTokenStore(dynamoClient, axonDynamoProperties.axonStorageTableName!!, claimTimeout = axonDynamoProperties.claimTimeout!!)
    }

}