package assaabloy.globalsolutions.axon.extension.dynamodb

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import java.util.concurrent.atomic.AtomicBoolean

class EventSequenceStore(private val client: DynamoDbClient, private val tableName: String) {

    companion object {
        private val SEQUENCE_VALUE = DynamoAttribute.Long("value")
        val SEQUENCE_NAME = DynamoAttribute.String("hk")
        val SORT_KEY = DynamoAttribute.Long("sk")
    }

    private val isInitialized = AtomicBoolean(false)

    private val sequenceKey = mapOf(
        SEQUENCE_NAME.valuePair("s:globalEventSequence"),
        SORT_KEY.valuePair(0)
    )

    fun readGlobalEventSequence(size: Int): LongRange {
        require(size > 0) { "The sequence size must be min 1" }

        synchronized(this) {
            if (!isInitialized.get()) {
                initializeSequence()
                isInitialized.set(true)
            }
        }

        return client.updateItem {
            it.tableName(tableName)
                .returnValues(ReturnValue.UPDATED_OLD)
                .expressionAttributeValues(
                    mapOf(":size" to numberAttributeValue(size))
                )
                .expressionAttributeNames(
                    mapOf("#v" to "value")
                )
                .key(sequenceKey)
                .updateExpression("SET #v = #v + :size")
        }.let {
            SEQUENCE_VALUE.from(it.attributes())
        }.let { it + 1..it + size }
    }

    private fun initializeSequence() {
        val getSequenceResponse = client.getItem {
            it.tableName(tableName).key(sequenceKey)
        }

        if (!getSequenceResponse.hasItem()) {
            client.putItem {
                it.tableName(tableName).item(sequenceKey + mapOf(SEQUENCE_VALUE.valuePair(0L)))
            }
        }
    }
}