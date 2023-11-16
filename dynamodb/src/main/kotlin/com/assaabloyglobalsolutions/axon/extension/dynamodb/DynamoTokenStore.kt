package com.assaabloyglobalsolutions.axon.extension.dynamodb

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
            claim(processorName, segment, trackingToken, false)
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

    override fun extendClaim(processorName: String, segment: Int) {
        measureTimedValue {
            claim(processorName, segment, null, false)
        }.also {
            logger.debug {
                "Extend claim $processorName[$segment] from node $nodeHash " +
                        "returning (in ${it.duration.inWholeMilliseconds} ms) tracking token: ${it.value}"
            }
        }
    }

    /**
     * Seems to be mainly used for processor/segments not owned by this node, to check if there is anything that
     * could be claimed
     */
    override fun fetchToken(processorName: String, segment: Int): TrackingToken = measureTimedValue {
        claim(processorName, segment, null, true) ?: GapAwareTrackingToken(0, mutableListOf())
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

    private fun claim(
        processorName: String,
        segment: Int,
        trackingToken: TrackingToken?,
        considerClaimTimeout: Boolean = false
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

            val expressionAttributeValues = mutableMapOf(":nodeId" to stringAttributeValue(nodeId))
            expressionAttributeValues += updateFields.mapKeys { ":${it.key}" }

            val conditionalExpression = when (considerClaimTimeout) {
                true -> {
                    expressionAttributeValues += ":time" to numberAttributeValue((clock.instant() - claimTimeout).toEpochMilli())
                    "attribute_not_exists($OWNER) or contains($OWNER, :nodeId) or $TIMESTAMP < :time"
                }

                false -> "attribute_not_exists($OWNER) or contains($OWNER, :nodeId)"
            }

            val result = client.updateItem { request ->
                request.returnValues(ReturnValue.ALL_OLD)
                    .tableName(tableName)
                    .conditionExpression(conditionalExpression)
                    .updateExpression(updateFields.map { "${it.key} = :${it.key}" }.joinToString(prefix = "SET "))
                    .expressionAttributeValues(expressionAttributeValues)
                    .key(
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
                    .conditionExpression("attribute_not_exists($OWNER) or contains($OWNER, :nodeId)")
                    .expressionAttributeValues(mapOf(":nodeId" to stringAttributeValue(nodeId)))
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
