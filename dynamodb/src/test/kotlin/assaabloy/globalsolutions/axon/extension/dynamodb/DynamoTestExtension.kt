package assaabloy.globalsolutions.axon.extension.dynamodb

import org.junit.jupiter.api.extension.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI

/**
 * To use the dynamodb extension, add the following to the test class:
 *
 * ```kotlin
 *     companion object {
 *         @RegisterExtension
 *         val dynamo = DynamoTestExtension(DynamoTableInitializer::initTables)
 *     }
 * ```
 *
 * Tests should use the [DynamoTestExtension.client], or else testcontainers will
 * fail connecting to the remapped dynamo port.
 *
 * This extension manages a dynamodb-local testcontainer, unless the system property
 * `TESTCONTAINERS_SKIP` is set to `true`. This can be set from a maven profile to
 * avoid running testcontainers from inside gitlab pipeline.
 *
 * Note that we rely on the testcontainers instance to be killed automatically when
 * all the tests have finished running. Doing it within an `AfterAllCallback` runs
 * once per test class, making the dynamodb client unusable after the first test
 * class - as the testcontainers instance must be started prior to `BeforeAllCallback`.
 */
class DynamoTestExtension(val onStart: (DynamoDbClient) -> Unit) : Extension {
    init {
        System.setProperty("aws.accessKeyId", "kid")
        System.setProperty("aws.secretAccessKey", "sak")
        System.setProperty("aws.sessionToken", "st")
    }

    @Suppress("HttpUrlsUsage")
    val client: DynamoDbClient by lazy {
        val endpointUri = if (testcontainersEnabled) {
            container.start()
            "http://${container.host}:${container.getMappedPort(8000)}"
        } else {
            "http://${System.getenv()["DB_HOST"] ?: "localhost"}:8000"
        }

        DynamoDbClient.builder()
            .region(Region.EU_WEST_1)
            .endpointOverride(URI.create(endpointUri))
            .build()
            .also(onStart)
    }

    val testcontainersEnabled = System.getenv()["TESTCONTAINERS_SKIP"] != "true"

    private val container: GenericContainer<*> = GenericContainer(
        DockerImageName.parse("amazon/dynamodb-local")
    ).withExposedPorts(8000)
}