package com.assaabloy.globalsolutions.axon.extension.dynamodb

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient


@Testcontainers
open class DynamoTest {

    companion object {
        @RegisterExtension
        val dynamo = DynamoTestExtension()
    }

    val client: DynamoDbClient = dynamo.client

    init {
        System.setProperty("aws.accessKeyId", "kid")
        System.setProperty("aws.secretAccessKey", "sak")
        System.setProperty("aws.sessionToken", "st")
    }

    @BeforeEach
    internal fun setUp() {
        DynamoTableInitializer.initTables(client)
    }

    @AfterEach
    internal fun tearDown() {
        DynamoTableInitializer.dropTables(client)
    }
}