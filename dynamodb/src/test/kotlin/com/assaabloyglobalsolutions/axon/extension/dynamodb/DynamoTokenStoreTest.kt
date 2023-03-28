package com.assaabloyglobalsolutions.axon.extension.dynamodb

import com.assaabloyglobalsolutions.axon.extension.dynamodb.DynamoTableInitializer.Companion.AXON_STORAGE
import org.axonframework.eventhandling.GapAwareTrackingToken
import org.axonframework.eventhandling.GlobalSequenceTrackingToken
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

internal class DynamoTokenStoreTest : DynamoTest() {

    @Test
    fun `fetch token happy path`() {
        val nodeOneStore = DynamoTokenStore(client, AXON_STORAGE, uuid())

        val token = GapAwareTrackingToken(12, listOf(8, 11))
        nodeOneStore.storeToken(token, "TestProcessor", 0)

        val tokenFromStore = nodeOneStore.fetchToken("TestProcessor", 0)
        assertEquals(token, tokenFromStore)
    }

    @Test
    fun `store token should fail when claimed by other`() {
        val nodeOneStore = DynamoTokenStore(client, AXON_STORAGE, uuid())
        val nodeTwoStore = DynamoTokenStore(client, AXON_STORAGE, uuid())

        nodeOneStore.storeToken(GlobalSequenceTrackingToken(0), "TestProcessor", 0)

        assertThrows<UnableToClaimTokenException> {
            nodeTwoStore.storeToken(GlobalSequenceTrackingToken(0), "TestProcessor", 0)
        }

        nodeOneStore.releaseClaim("TestProcessor", 0)
        nodeTwoStore.storeToken(GlobalSequenceTrackingToken(0), "TestProcessor", 0)
    }

    @Test
    fun `fetch token should fail when claimed by other`() {
        val nodeOneStore = DynamoTokenStore(client, AXON_STORAGE, uuid())
        val nodeTwoStore = DynamoTokenStore(client, AXON_STORAGE, uuid())

        nodeOneStore.storeToken(GlobalSequenceTrackingToken(0), "TestProcessor", 0)

        assertThrows<UnableToClaimTokenException> {
            nodeTwoStore.fetchToken("TestProcessor", 0)
        }

        nodeOneStore.releaseClaim("TestProcessor", 0)
        nodeTwoStore.fetchToken("TestProcessor", 0)
    }

    @Test
    fun `list segments for processor with multiple segments`() {
        val nodeOneStore = DynamoTokenStore(client, AXON_STORAGE, uuid())
        val nodeTwoStore = DynamoTokenStore(client, AXON_STORAGE, uuid())

        nodeOneStore.storeToken(GlobalSequenceTrackingToken(0), "TestProcessor", 0)
        nodeOneStore.storeToken(GlobalSequenceTrackingToken(0), "TestProcessor", 1)
        nodeOneStore.storeToken(GlobalSequenceTrackingToken(0), "TestProcessor", 3)

        assertArrayEquals(intArrayOf(0, 1, 3), nodeOneStore.fetchSegments("TestProcessor"))

        // Claim should not stop other nodes from listing
        assertArrayEquals(intArrayOf(0, 1, 3), nodeTwoStore.fetchSegments("TestProcessor"))
    }

    @Test
    fun `claim should time out during store`() {
        val nodeOneStore = DynamoTokenStore(client, AXON_STORAGE, uuid(), Duration.ofMillis(500))
        val nodeTwoStore = DynamoTokenStore(client, AXON_STORAGE, uuid(), Duration.ofMillis(500))

        nodeOneStore.storeToken(
            GlobalSequenceTrackingToken(0),
            "TestProcessor",
            0
        )

        assertThrows<UnableToClaimTokenException> {
            nodeTwoStore.storeToken(
                GlobalSequenceTrackingToken(0),
                "TestProcessor",
                0
            )
        }

        assertEventually {
            nodeTwoStore.storeToken(
                GlobalSequenceTrackingToken(0),
                "TestProcessor",
                0
            )
        }
    }

    @Test
    fun `claim should time out during fetch`() {
        val nodeOneStore = DynamoTokenStore(client, AXON_STORAGE, uuid(), Duration.ofMillis(500))
        val nodeTwoStore = DynamoTokenStore(client, AXON_STORAGE, uuid(), Duration.ofMillis(500))

        nodeOneStore.storeToken(
            GlobalSequenceTrackingToken(0),
            "TestProcessor",
            0
        )

        assertThrows<UnableToClaimTokenException> {
            nodeTwoStore.fetchToken("TestProcessor", 0)
        }

        assertEventually {
            nodeTwoStore.fetchToken("TestProcessor", 0)
        }
    }
}