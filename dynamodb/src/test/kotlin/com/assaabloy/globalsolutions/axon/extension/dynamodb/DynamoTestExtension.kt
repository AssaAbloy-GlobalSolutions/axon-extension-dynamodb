package com.assaabloy.globalsolutions.axon.extension.dynamodb

import org.junit.jupiter.api.extension.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI

class DynamoTestExtension : Extension, BeforeAllCallback, AfterAllCallback {
    lateinit var client: DynamoDbClient
        private set

    val testcontainersEnabled = System.getenv()["testcontainers.skip"] != "true"

    init {
        System.setProperty("aws.accessKeyId", "kid")
        System.setProperty("aws.secretAccessKey", "sak")
        System.setProperty("aws.sessionToken", "st")
    }

    val container: GenericContainer<*> = GenericContainer(
        DockerImageName.parse("amazon/dynamodb-local")
    ).withExposedPorts(8000)

    override fun beforeAll(context: ExtensionContext?) {
        val endpointUri = if (testcontainersEnabled) {
            container.start()
            "http://${container.host}:${container.getMappedPort(8000)}"
        } else {
            "http://${System.getenv()["DB_HOST"] ?: "localhost"}:8000"
        }

        client = DynamoDbClient.builder()
            .region(Region.EU_WEST_1)
            .endpointOverride(URI.create(endpointUri))
            .build()
    }

    override fun afterAll(context: ExtensionContext?) {
        if (testcontainersEnabled)
            container.stop()
    }
}