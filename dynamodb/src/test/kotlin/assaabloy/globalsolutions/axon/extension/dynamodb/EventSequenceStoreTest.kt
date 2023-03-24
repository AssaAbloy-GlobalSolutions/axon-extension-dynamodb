package assaabloy.globalsolutions.axon.extension.dynamodb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

internal class EventSequenceStoreTest : DynamoTest() {

    @Test
    fun `sequence should be created if missing`() {
        val eventSequenceStore = EventSequenceStore(client, DynamoTableInitializer.AXON_STORAGE)
        assertThat(eventSequenceStore.readGlobalEventSequence(2)).isEqualTo(1L..2L)
    }

    @Test
    fun `sequence numbers should be unique and without gaps during parallel processing`() {
        val eventSequenceStore = EventSequenceStore(client, DynamoTableInitializer.AXON_STORAGE)

        val ranges = runBlocking(Dispatchers.Default) {
            (0..99).map {
                async {
                    eventSequenceStore.readGlobalEventSequence(Random().nextInt(5) + 1)
                }
            }.awaitAll()
        }
        assertThat(ranges).hasSize(100)

        // check for duplicates
        val numbers = mutableListOf<Long>()
        ranges.forEach { range ->
            range.forEach {
                assertThat(numbers).doesNotContain(it)
                numbers += it
            }
        }

        // check for gaps
        numbers.sorted().zipWithNext { a, b ->
            assertThat(b).isEqualTo(a + 1)
        }
    }
}
