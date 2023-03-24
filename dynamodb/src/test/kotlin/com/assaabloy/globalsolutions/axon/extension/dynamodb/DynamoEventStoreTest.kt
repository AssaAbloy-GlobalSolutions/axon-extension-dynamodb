package com.assaabloy.globalsolutions.axon.extension.dynamodb

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventhandling.GapAwareTrackingToken
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.modelling.command.AggregateStreamCreationException
import org.axonframework.modelling.command.ConcurrencyException
import org.axonframework.serialization.json.JacksonSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DynamoEventStoreTest : DynamoTest() {

    private val eventStore = DynamoStorageEngine.builder()
        .dynamoClient(client)
        .jacksonObjectMapper(jacksonObjectMapper().registerModule(JavaTimeModule()))
        .eventPayloadPackagePrefix("com.assaabloy.globalsolutions.axon.extension")
        .eventSerializer(JacksonSerializer.defaultSerializer())
        .snapshotSerializer(JacksonSerializer.defaultSerializer())
        .tableName(DynamoTableInitializer.AXON_STORAGE)
        .indexName(DynamoTableInitializer.EVENTS_GSI_NAME)
        .batchSize(100)
        .build()

    @Test
    fun `reading many batches of events gives a consistent event sequence`() {
        val aid = uuid()

        for (i in 0..30) {
            eventStore.appendEvents(testEvent(aid, i.toLong(), i.toString()))
        }

        val smallBatchEventStore = DynamoStorageEngine.builder()
            .dynamoClient(client)
            .jacksonObjectMapper(jacksonObjectMapper().registerModule(JavaTimeModule()))
            .eventPayloadPackagePrefix("com.assaabloy.globalsolutions.axon.extension")
            .eventSerializer(JacksonSerializer.defaultSerializer())
            .snapshotSerializer(JacksonSerializer.defaultSerializer())
            .tableName(DynamoTableInitializer.AXON_STORAGE)
            .indexName(DynamoTableInitializer.EVENTS_GSI_NAME)
            .batchSize(5)
            .build()

        val events = smallBatchEventStore.readEvents(aid).asStream().toList()
        assertThat(events).hasSize(31)

        events.forEachIndexed { index, eventMessage ->
            assertThat(eventMessage.sequenceNumber).isEqualTo(index.toLong())
        }
    }

    @Test
    fun `creating two aggregates with same aggregate id should fail`() {
        val aid = uuid()

        assertThrows<AggregateStreamCreationException> {
            eventStore.appendEvents(testEvent(aid, 0))
            eventStore.appendEvents(testEvent(aid, 0))
        }
    }

    @Test
    fun `saving two event with the same sequence number should cause concurrency exception`() {
        val aid = saveTestAggregate(2)

        assertThrows<ConcurrencyException> {
            eventStore.appendEvents(testEvent(aid, 1))
        }
    }

    @Test
    fun `saving two event with the same sequence number in batch should cause concurrency exception`() {
        val aid = saveTestAggregate(2)

        assertThrows<ConcurrencyException> {
            eventStore.appendEvents(testEvent(aid, 1), testEvent(aid, 2))
        }
    }

    @Test
    fun `read all events with no previous tracking token`() {
        saveTestAggregate(5)

        val events = eventStore.readEvents(null, true).toList()
        assertThat(events).hasSize(5)

        // Check that there are no more events
        assertThat(eventStore.readEvents(events.last().trackingToken(), true)).isEmpty()
    }

    @Test
    fun `read all events with from tracking token`() {
        saveTestAggregate(5)

        val events = eventStore.readEvents(GapAwareTrackingToken(2, emptyList()), true).toList()
        assertThat(events).hasSize(3)

        // Check that there are no more events
        assertThat(eventStore.readEvents(events.last().trackingToken(), true)).isEmpty()
    }

    @Test
    fun `read events when there are more than one batch of events available`() {
        repeat(8) {
            saveTestAggregate(20)
        }

        val stream = eventStore.readEvents(null, true)
        assertThat(stream.count()).isEqualTo(160)
    }

    @Test
    fun `read events when there are more than one batch of events available also with gap in sequences`() {
        repeat(9) {
            saveTestAggregate(10)
        }


        val eventSequenceStore = EventSequenceStore(client, DynamoTableInitializer.AXON_STORAGE)
        eventSequenceStore.readGlobalEventSequence(20)

        repeat(7) {
            saveTestAggregate(10)
        }

        val stream = eventStore.readEvents(null, true)
        assertThat(stream.count()).isEqualTo(160)
    }

    @Test
    fun `read events with single gap`() {
        saveTestAggregate(5)

        val events = eventStore.readEvents(GapAwareTrackingToken(3, listOf(1)), true).toList()
        assertThat(events).hasSize(3)

        // Check that the events are ordered, since we only use on aggregate we can check the event sequence numbers
        events.map {
            (it as GenericDomainEventMessage<*>).sequenceNumber
        }.zipWithNext { first, second ->
            assertThat(first < second).isTrue
        }
    }

    @Test
    fun `read events with only gaps, no new events at the end of the stream`() {
        saveTestAggregate(5)

        val events = eventStore.readEvents(GapAwareTrackingToken(5, listOf(1, 2, 3, 4)), true).toList()
        assertThat(events).hasSize(4)

        // Check that the events are ordered, since we only use on aggregate we can check the event sequence numbers
        events.map {
            (it as GenericDomainEventMessage<*>).sequenceNumber
        }.zipWithNext { first, second ->
            assertThat(first < second).isTrue
        }
    }

    private fun saveTestAggregate(numberOfEvents: Long, aid: String = uuid()): String {
        (1L..numberOfEvents).map {
            testEvent(aid, it - 1)
        }.also {
            eventStore.appendEvents(it)
        }

        return aid
    }

    private fun testEvent(aid: String, sequenceNumber: Long, text: String = uuid()) =
        GenericDomainEventMessage(TestEvent::class.java.simpleName, aid, sequenceNumber, TestEvent(text))
}

data class TestEvent(val text: String)
