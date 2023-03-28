package com.assaabloyglobalsolutions.axon.extension.dynamodb

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient


@Testcontainers
open class DynamoTest {

    companion object {
        @RegisterExtension
        val dynamo = DynamoTestExtension(DynamoTableInitializer.Companion::initTables)
    }

    val client: DynamoDbClient = dynamo.client

    @BeforeEach
    internal fun setUp() {
        DynamoTableInitializer.initTables(client)
    }

    @AfterEach
    internal fun tearDown() {
        DynamoTableInitializer.dropTables(client)
    }
}