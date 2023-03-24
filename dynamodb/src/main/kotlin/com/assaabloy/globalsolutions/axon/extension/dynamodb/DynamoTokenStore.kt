package com.assaabloy.globalsolutions.axon.extension.dynamodb

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import org.axonframework.eventhandling.GapAwareTrackingToken
import org.axonframework.eventhandling.TrackingToken
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import java.lang.management.ManagementFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue


@OptIn(ExperimentalTime::class)
class DynamoTokenStore(
    private val client: DynamoDbClient,
    private val tableName: String,
    private val nodeId: String = ManagementFactory.getRuntimeMXBean().name,
    private var claimTimeout: Duration = Duration.ofSeconds(10)
) : TokenStore {

    /**
     * hk = processor name (hash key)
     * sk = segment (sort key)
     * o = owner that has claimed the segment
     * ts = timestamp when the segment was claimed
     * tt = tracking token
     * ttc = tracking token class
     */
    private val PROCESSOR_NAME = DynamoAttribute.String("hk")
    private val SEGMENT = DynamoAttribute.Int("sk")
    private val OWNER = DynamoAttribute.String("o")
    private val TIMESTAMP = DynamoAttribute.Long("ts")
    private val TRACKING_TOKEN = DynamoAttribute.String("tt")
    private val TRACKING_TOKEN_CLASS = DynamoAttribute.Class("ttc")

    private val objectMapper = jacksonObjectMapper()
    private val logger = KotlinLogging.logger {}
    private val clock = Clock.systemUTC()

    private val nodeHash = "#${nodeId.hashCode()}"

    override fun storeToken(trackingToken: TrackingToken?, processorName: String, segment: Int) {
        measureTimedValue {
            claim(processorName, segment, trackingToken)
        }.also {
            logger.debug {
                "Store token for $processorName[$segment] from node $nodeHash " +
                        "returning (in ${it.duration.inWholeMilliseconds} ms) tracking token: ${it.value}"
            }
        }
    }

    override fun releaseClaim(processorName: String, segment: Int) {
        measureTimedValue {
            release(processorName, segment)
        }.also {
            logger.debug {
                "Released claim on $processorName[$segment] from node $nodeHash, " +
                        "in ${it.duration.inWholeMilliseconds} ms"
            }
        }
    }

    /**
     * Extend a claim already held by the node, run about once per second when the application is not producing events
     */
    override fun extendClaim(processorName: String, segment: Int) {
        measureTimedValue {
            claim(processorName, segment, null)
        }.also {
            logger.debug {
                "Extend claim $processorName[$segment] from node $nodeHash " +
                        "returning (in ${it.duration.inWholeMilliseconds} ms) tracking token: ${it.value}"
            }
        }
    }

    /**
     * Seems to be mainly used for processor/segments not owned by this node, to check if there is anything that
     * could be claim
     */
    override fun fetchToken(processorName: String, segment: Int): TrackingToken = measureTimedValue {
        val result = client.getItem {
            it.tableName(tableName).key(
                mapOf(
                    PROCESSOR_NAME.valuePair("t:$processorName"),
                    SEGMENT.valuePair(segment)
                )
            )
        }

        val trackingToken = result?.takeIf { it.hasItem() }
            ?.item()
            ?.takeIf { it.isNotEmpty() }
            ?.also {
                val owner = OWNER.nullableFrom(it)
                val timestamp = TIMESTAMP.from(it)

                if (owner != null && owner != nodeId && claimNotExpired(timestamp)) {
                    logger.debug { "Fetch $processorName[$segment] from node $nodeHash failed, owned by #${owner.hashCode()}" }
                    throw UnableToClaimTokenException("Unable to claim token $processorName[$segment], it is owned by $owner")
                }
            }
            ?.resolveTrackingToken()

        trackingToken ?: GapAwareTrackingToken(0, mutableListOf())
    }.also {
        logger.debug {
            "Fetch $processorName[$segment] from node $nodeHash " +
                    "returning (in ${it.duration.inWholeMilliseconds} ms) tracking token: ${it.value}"
        }
    }.value

    /**
     * Run every ~5 seconds to see if the processor has been split/merged
     */
    override fun fetchSegments(processorName: String): IntArray = measureTimedValue {
        client.query {
            it.tableName(tableName)
                .keyConditionExpression("$PROCESSOR_NAME = :processorName")
                .expressionAttributeValues(
                    mapOf(":processorName" to stringAttributeValue("t:$processorName"))
                )
        }
            .items()
            .map { SEGMENT.from(it) }
            .toIntArray()
    }.also {
        logger.debug {
            "Fetch segments for processor $processorName from node $nodeHash " +
                    "returning (in ${it.duration.inWholeMilliseconds} ms) ${it.value.toList()}"
        }
    }.value

    override fun retrieveStorageIdentifier(): Optional<String> {
        return Optional.of("DynamoTokenStore")
    }

    override fun requiresExplicitSegmentInitialization(): Boolean {
        return false
    }

    private fun claimNotExpired(timestamp: Long): Boolean {
        return Instant.ofEpochMilli(timestamp).plus(claimTimeout).isAfter(clock.instant())
    }

    private fun claim(
        processorName: String,
        segment: Int,
        trackingToken: TrackingToken?
    ): TrackingToken? {
        try {
            val updateFields = mutableMapOf(
                OWNER.valuePair(nodeId),
                TIMESTAMP.valuePair(clock.instant().toEpochMilli())
            )

            if (trackingToken != null) {
                updateFields += TRACKING_TOKEN.valuePair(objectMapper.writeValueAsString(trackingToken))
                updateFields += TRACKING_TOKEN_CLASS.valuePair(trackingToken.javaClass)
            }

            val result = client.updateItem {
                it.returnValues(ReturnValue.ALL_OLD).tableName(tableName)
                    .conditionExpression("attribute_not_exists($OWNER) or contains($OWNER, :nodeId) or $TIMESTAMP < :time")
                    .updateExpression(updateFields.map { "${it.key} = :${it.key}" }.joinToString(prefix = "SET "))
                    .expressionAttributeValues(
                        mapOf(
                            ":nodeId" to stringAttributeValue(nodeId),
                            ":time" to numberAttributeValue((clock.instant() - claimTimeout).toEpochMilli())
                        ) + updateFields.mapKeys { ":${it.key}" }
                    ).key(
                        mapOf(
                            PROCESSOR_NAME.valuePair("t:$processorName"),
                            SEGMENT.valuePair(segment)
                        )
                    )
            }

            return result.takeIf { it.hasAttributes() && TRACKING_TOKEN in it.attributes() }
                ?.attributes()
                ?.resolveTrackingToken()
        } catch (e: ConditionalCheckFailedException) {
            logger.debug { "Claim $processorName[$segment] from node $nodeHash failed" }
            throw UnableToClaimTokenException(e.message)
        } catch (e: Exception) {
            logger.error(e) { "Failed to claim $processorName[$segment]" }
            throw e
        }
    }

    private fun release(processorName: String, segment: Int) {
        try {
            client.updateItem {
                it.tableName(tableName)
                    .conditionExpression("attribute_not_exists($OWNER) or contains($OWNER, :nodeId) or $TIMESTAMP < :time")
                    .expressionAttributeValues(
                        mapOf(
                            ":nodeId" to stringAttributeValue(nodeId),
                            ":time" to numberAttributeValue(clock.instant().minus(claimTimeout).toEpochMilli())
                        )
                    )
                    .key(
                        mapOf(
                            PROCESSOR_NAME.valuePair("t:$processorName"),
                            SEGMENT.valuePair(segment)
                        )
                    )
                    .updateExpression("REMOVE $OWNER")
            }
        } catch (e: ConditionalCheckFailedException) {
            logger.debug { "Release claim on $processorName[$segment] from node $nodeHash failed, claim conditions failed" }
            throw UnableToClaimTokenException(e.message)
        } catch (e: Exception) {
            logger.error(e) { "Failed to release claim for $processorName[$segment]" }
            throw e
        }
    }

    private fun Map<String, AttributeValue>.resolveTrackingToken(): TrackingToken? {
        val tt = TRACKING_TOKEN.nullableFrom(this) ?: return null
        return objectMapper.readValue(tt, TRACKING_TOKEN_CLASS.from(this)) as TrackingToken
    }
}
