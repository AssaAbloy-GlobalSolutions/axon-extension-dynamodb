package com.assaabloyglobalsolutions.axon.extension.dynamodb

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.axonframework.common.Assert
import org.axonframework.common.jdbc.PersistenceExceptionResolver
import org.axonframework.eventhandling.*
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter
import org.axonframework.serialization.SerializedObject
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.upcasting.event.EventUpcaster
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.min
import kotlin.system.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

private const val GLOBAL_SEQUENCE_BUCKET_SIZE = 100

@OptIn(ExperimentalTime::class)
class DynamoStorageEngine private constructor(builder: Builder) : BatchingEventStorageEngine(builder) {

    companion object {
        /**
         * DynamoDB event table schema:
         * * hk: aggregate identifier, hash key
         * * sk: sequence number (of the event within the aggregate), sort key
         * * gsh: global event sequence number (high part), GSI hash key
         * * gsl: global event sequence number (low part), GSI sort key
         * * e: serialized event, using the EventData class
         */
        val AGGREGATE_IDENTIFIER = DynamoAttribute.String("hk")
        val SEQUENCE_NUMBER = DynamoAttribute.Long("sk")
        private val GLOBAL_SEQUENCE_HIGH = DynamoAttribute.Long("gsh")
        private val GLOBAL_SEQUENCE_LOW = DynamoAttribute.Long("gsl")
        private val EVENT = DynamoAttribute.ByteArray("e")

        fun builder(): Builder = Builder()
            .persistenceExceptionResolver(DynamoPersistenceExceptionResolver()) // Default error handler
    }


    private val logger = KotlinLogging.logger {}
    private val prefixReplacement = "*"

    private val client = builder.client
    private val objectMapper = builder.mapper
    private val tableName = builder.tName
    private val indexName = builder.idxName
    private val payloadPrefix = builder.eventPayloadPackagePrefix

    private val sequenceStore = EventSequenceStore(client, tableName)

    override fun appendEvents(events: List<EventMessage<*>>, serializer: Serializer) {
        if (events.isEmpty()) return

        measureTimeMillis {
            val globalSequenceIterator = sequenceStore.readGlobalEventSequence(events.size).iterator()

            // It is cheaper if we can avoid transactional write, using standard putItem if there is only one event
            if (events.size == 1) {
                putSingleEvent(events, globalSequenceIterator, serializer)
            } else {
                putEventBatch(events, globalSequenceIterator, serializer)
            }
        }.also {
            logger.debug { "Appended ${events.size} event(s) in $it ms" }
        }
    }

    override fun storeSnapshot(snapshot: DomainEventMessage<*>, serializer: Serializer) {
        error("Not yet implemented")
    }

    override fun readSnapshotData(aggregateId: String): Stream<out DomainEventData<*>> {
        logger.debug { "Read snapshot data for aggregate $aggregateId" }
        return Stream.empty()
    }

    override fun fetchDomainEvents(
        aggregateId: String,
        firstSequenceNumber: Long,
        batchSize: Int
    ): List<DomainEventData<*>> = measureTimedValue {
        client.query {
            it.tableName(tableName)
                .keyConditionExpression("$AGGREGATE_IDENTIFIER = :aid and $SEQUENCE_NUMBER >= :from")
                .limit(batchSize)
                .consistentRead(true)
                .expressionAttributeValues(
                    mapOf(
                        ":aid" to stringAttributeValue("e:$aggregateId"),
                        ":from" to numberAttributeValue(firstSequenceNumber)
                    )
                )
        }.items().map { getDomainEventData(it) }
    }.also {
        logger.debug {
            "Fetched ${it.value.size} domain events for aggregate $aggregateId " +
                    "from sequence $firstSequenceNumber, in ${it.duration.inWholeMilliseconds} ms"
        }
    }.value

    override fun fetchTrackedEvents(lastToken: TrackingToken?, batchSize: Int): List<TrackedEventData<*>> =
        measureTimedValue {
            Assert.isTrue(lastToken == null || lastToken is GapAwareTrackingToken) {
                "Unsupported token format: $lastToken"
            }

            var previousToken: GapAwareTrackingToken? = lastToken as? GapAwareTrackingToken
            val globalIndex = previousToken?.index

            val items = when {
                previousToken == null -> readEventDataWithoutToken()
                previousToken.gaps.isEmpty() -> runBlocking {
                    readEventDataWithoutGaps(globalIndex!!)
                }

                else -> readEventDataWithGaps(previousToken.index, previousToken.gaps.toList())
            }

            val domainItems = items.map {
                getDomainEventData(it) to (GLOBAL_SEQUENCE_HIGH.from(it) * GLOBAL_SEQUENCE_BUCKET_SIZE) + GLOBAL_SEQUENCE_LOW.from(
                    it
                )
            }.sortedBy { it.second }

            val results: MutableList<TrackedEventData<*>> = mutableListOf()
            for (domainItem in domainItems) {
                val next = getTrackedEventData(domainItem.first, domainItem.second, previousToken)
                results.add(next)
                previousToken = next.trackingToken() as GapAwareTrackingToken
            }

            results
        }.also {
            logger.debug { "Fetched ${it.value.size} tracked events from $lastToken, in ${it.duration.inWholeMilliseconds} ms" }
        }.value

    private fun putEventBatch(
        events: List<EventMessage<*>>,
        globalSequenceIterator: LongIterator,
        serializer: Serializer
    ) {
        try {
            client.transactWriteItems {
                events.map { event ->
                    TransactWriteItem.builder().put { put ->
                        put.tableName(tableName)
                            .conditionExpression("attribute_not_exists($SEQUENCE_NUMBER)")
                            .item(
                                toItemMap(
                                    asDomainEventMessage(event),
                                    globalSequenceIterator.nextLong(),
                                    serializer
                                )
                            )
                    }.build()
                }.let { writeItems ->
                    it.transactItems(writeItems)
                }
            }
        } catch (e: DynamoDbException) {
            // NOTE: we cant tell which event caused the failure...
            handlePersistenceException(e, events.first())
        }
    }

    private fun putSingleEvent(
        events: List<EventMessage<*>>,
        globalSequenceIterator: LongIterator,
        serializer: Serializer
    ) {
        try {
            client.putItem {
                it.tableName(tableName)
                    .conditionExpression("attribute_not_exists($SEQUENCE_NUMBER)")
                    .item(
                        toItemMap(
                            asDomainEventMessage(events.first()),
                            globalSequenceIterator.nextLong(),
                            serializer
                        )
                    )
            }
        } catch (e: DynamoDbException) {
            handlePersistenceException(e, events.first())
        }
    }

    private suspend fun readEventDataWithoutGaps(globalIndex: Long): List<Map<String, AttributeValue>> {
        // Make sure not to get stuck @ 0,99 ... need to look in to next gsh
        val (gsh, gsl) = splitGlobalSequence(globalIndex + if (globalIndex == 0L) 0 else 1)

        val first = client.query {
            it.tableName(tableName).indexName(indexName)
                .keyConditionExpression("$GLOBAL_SEQUENCE_HIGH = :gsh and $GLOBAL_SEQUENCE_LOW >= :gsl")
                .expressionAttributeValues(
                    mapOf(
                        ":gsh" to numberAttributeValue(gsh),
                        ":gsl" to numberAttributeValue(gsl)
                    )
                )
        }.items()

        return if (first.isEmpty() && gsl > (GLOBAL_SEQUENCE_BUCKET_SIZE * 4 / 5)) {
            // Need to look ahead, there might be gaps in the sequence...
            client.query {
                it.tableName(tableName).indexName(indexName)
                    .keyConditionExpression("$GLOBAL_SEQUENCE_HIGH = :gsh and $GLOBAL_SEQUENCE_LOW >= :gsl")
                    .expressionAttributeValues(
                        mapOf(
                            ":gsh" to numberAttributeValue(gsh + 1),
                            ":gsl" to numberAttributeValue(0)
                        )
                    )
            }.items()
        } else {
            first
        }
    }

    private fun readEventDataWithGaps(globalIndex: Long, gaps: List<Long>): List<Map<String, AttributeValue>> =
        runBlocking {
            val jobs = gaps.map {
                async { readGap(it) }
            } + async { readEventDataWithoutGaps(globalIndex) }

            jobs.flatMap { it.await() }
        }

    private suspend fun readGap(index: Long): List<Map<String, AttributeValue>> =
        splitGlobalSequence(index)
            .let { (gsh, gsl) ->
                client.query {
                    it.tableName(tableName).indexName(indexName)
                        .keyConditionExpression("$GLOBAL_SEQUENCE_HIGH = :gsh and $GLOBAL_SEQUENCE_LOW = :gsl")
                        .expressionAttributeValues(
                            mapOf(
                                ":gsh" to numberAttributeValue(gsh),
                                ":gsl" to numberAttributeValue(gsl)
                            )
                        )
                }.items()
            }

    private fun readEventDataWithoutToken(): List<Map<String, AttributeValue>> = client.query {
        it.tableName(tableName).indexName(indexName)
            .keyConditionExpression("$GLOBAL_SEQUENCE_HIGH = :gsh")
            .expressionAttributeValues(mapOf(":gsh" to numberAttributeValue(0)))
    }.items()

    private fun splitGlobalSequence(globalSequence: Long): Pair<Long, Long> =
        (globalSequence / GLOBAL_SEQUENCE_BUCKET_SIZE) to (globalSequence % GLOBAL_SEQUENCE_BUCKET_SIZE)

    @OptIn(ExperimentalStdlibApi::class)
    private fun getTrackedEventData(
        domainEvent: GenericDomainEventEntry<*>,
        globalSequence: Long,
        previousToken: GapAwareTrackingToken?
    ): TrackedEventData<*> {
        // Now that we have the event itself, we can calculate the token.
        val allowGaps = domainEvent.timestamp.isAfter(gapTimeoutFrame())
        var token = previousToken
        if (token == null) {
            token = GapAwareTrackingToken.newInstance(
                globalSequence,
                if (allowGaps) {
                    (min(0L, globalSequence)..<globalSequence).toList()
                } else {
                    emptyList()
                }
            )
        } else {
            token = token.advanceTo(globalSequence, 10000)
            if (!allowGaps) {
                token = token.withGapsTruncatedAt(globalSequence)
            }
        }
        return TrackedDomainEventData(token, domainEvent)
    }

    private fun gapTimeoutFrame(): Instant = GenericEventMessage.clock.instant().minus(60000, ChronoUnit.MILLIS)

    private fun getDomainEventData(item: Map<String, AttributeValue>): GenericDomainEventEntry<*> {
        val eventData = EVENT.from(item).let {
            objectMapper.readValue<EventData>(gunzip(it))
        }

        return GenericDomainEventEntry(
            eventData.eventType,
            AGGREGATE_IDENTIFIER.from(item).removePrefix("e:"),
            SEQUENCE_NUMBER.from(item),
            eventData.id,
            eventData.timestamp,
            eventData.payloadTypeName.replacePrefix(prefixReplacement, payloadPrefix ?: ""),
            eventData.payloadRevision,
            eventData.payload,
            eventData.metadata
        )
    }

    private fun <T> asDomainEventMessage(event: EventMessage<T>): DomainEventMessage<T> =
        event as? DomainEventMessage<T>
            ?: GenericDomainEventMessage(null, event.identifier, 0L, event) { event.timestamp }

    private fun toItemMap(
        event: DomainEventMessage<*>,
        globalSequence: Long,
        serializer: Serializer
    ): Map<String, AttributeValue> {

        val payload: SerializedObject<ByteArray> = event.serializePayload(serializer, ByteArray::class.java)
        val metaData: SerializedObject<ByteArray> = event.serializeMetaData(serializer, ByteArray::class.java)
        val (gsh, gsl) = splitGlobalSequence(globalSequence)

        return mapOf(
            AGGREGATE_IDENTIFIER.valuePair("e:${event.aggregateIdentifier}"),
            SEQUENCE_NUMBER.valuePair(event.sequenceNumber),
            GLOBAL_SEQUENCE_HIGH.valuePair(gsh),
            GLOBAL_SEQUENCE_LOW.valuePair(gsl),
            EVENT.valuePair(
                gzip(
                    objectMapper.writeValueAsBytes(
                        EventData(
                            event.identifier,
                            event.type,
                            event.timestamp,
                            payload.type.name.replacePrefix(payloadPrefix, prefixReplacement),
                            payload.type.revision ?: "",
                            payload.data,
                            metaData.data
                        )
                    )
                )
            )
        )
    }

    class Builder : BatchingEventStorageEngine.Builder() {
        lateinit var client: DynamoDbClient
        lateinit var mapper: ObjectMapper
        lateinit var tName: String
        lateinit var idxName: String

        var eventPayloadPackagePrefix: String? = null

        override fun snapshotSerializer(snapshotSerializer: Serializer?): Builder {
            super.snapshotSerializer(snapshotSerializer)
            return this
        }

        override fun upcasterChain(upcasterChain: EventUpcaster?): Builder {
            super.upcasterChain(upcasterChain)
            return this
        }

        override fun persistenceExceptionResolver(persistenceExceptionResolver: PersistenceExceptionResolver?): Builder {
            super.persistenceExceptionResolver(persistenceExceptionResolver)
            return this
        }

        override fun eventSerializer(eventSerializer: Serializer?): Builder {
            super.eventSerializer(eventSerializer)
            return this
        }

        override fun snapshotFilter(snapshotFilter: SnapshotFilter?): Builder {
            super.snapshotFilter(snapshotFilter)
            return this
        }

        override fun batchSize(batchSize: Int): Builder {
            super.batchSize(batchSize)
            return this
        }

        fun dynamoClient(client: DynamoDbClient): Builder {
            this.client = client
            return this
        }

        fun jacksonObjectMapper(objectMapper: ObjectMapper): Builder {
            this.mapper = objectMapper
            return this
        }

        fun eventPayloadPackagePrefix(eventPayloadPackagePrefix: String?): Builder {
            this.eventPayloadPackagePrefix = eventPayloadPackagePrefix
            return this
        }

        fun tableName(tableName: String): Builder {
            this.tName = tableName;
            return this
        }

        fun indexName(indexName: String): Builder {
            this.idxName = indexName;
            return this
        }

        fun build(): DynamoStorageEngine {
            check(this::client.isInitialized) { "dynamodbClient is required" }
            check(this::mapper.isInitialized) { "jacksonObjectMapper is required" }
            check(this::tName.isInitialized) { "tableName is required" }
            check(this::idxName.isInitialized) { "indexName is required" }

            return DynamoStorageEngine(this)
        }
    }

    fun gzip(content: ByteArray): ByteArray = ByteArrayOutputStream().let { bos ->
        GZIPOutputStream(bos).use { it.write(content) }
        bos.toByteArray()
    }

    fun gunzip(compressed: ByteArray): ByteArray = GZIPInputStream(compressed.inputStream()).readAllBytes()
}

private fun String.replacePrefix(prefix: String?, replacement: String): String {
    return prefix?.let {
        if (this.startsWith(it)) {
            replacement + this.removePrefix(it)
        } else {
            null
        }
    } ?: this
}

@Suppress("ArrayInDataClass")
data class EventData(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("et")
    val eventType: String,
    @JsonProperty("ts")
    val timestamp: Instant,
    @JsonProperty("ptn")
    val payloadTypeName: String,
    @JsonProperty("pr")
    val payloadRevision: String,
    @JsonProperty("p")
    val payload: ByteArray,
    @JsonProperty("m")
    val metadata: ByteArray
)

class DynamoPersistenceExceptionResolver : PersistenceExceptionResolver {
    override fun isDuplicateKeyViolation(e: Exception): Boolean {
        return e is ConditionalCheckFailedException || (e is TransactionCanceledException
                && e.cancellationReasons().map { it.code() }.contains("ConditionalCheckFailed"))
    }
}

