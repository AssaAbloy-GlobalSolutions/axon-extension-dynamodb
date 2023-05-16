/*
 * Copyright (c) 2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.assaabloyglobalsolutions.axon.extension.dynamodb.example

import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.api.AccountBalanceQuery
import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.api.CreateBankAccountCommand
import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.api.DepositMoneyCommand
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.bind.annotation.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

/**
 * Starting point.
 */
fun main(args: Array<String>) {
    runApplication<DynamoDbAxonExampleApplication>(*args)
}

/**
 * Main application class.
 */
@SpringBootApplication
@EnableScheduling
class DynamoDbAxonExampleApplication {

    @Bean
    fun registerKotlinModule(): Module = KotlinModule
        .Builder()
        .configure(KotlinFeature.SingletonSupport, true)
        .build()

    @Bean
    fun registerJavaTimeModule(): Module = JavaTimeModule()
}

@RestController
@RequestMapping("/health")
class HealthController {
    @GetMapping()
    fun health(): ResponseEntity<Any> = ResponseEntity.ok().build()
}

@RestController
@RequestMapping("/bank")
class SimpleController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {

    @PostMapping("/account", produces = [APPLICATION_JSON_VALUE])
    fun createAccount(): ResponseEntity<CreateAccountResponse> {
        val accountId = java.util.UUID.randomUUID().toString()

        commandGateway.send<String>(
            CreateBankAccountCommand(
                bankAccountId = accountId,
                overdraftLimit = 1000
            )
        )

        return ResponseEntity.ok(CreateAccountResponse(accountId))
    }

    @PostMapping("/account/deposit", consumes = [APPLICATION_JSON_VALUE])
    fun deposit(@RequestBody request: DepositRequest): ResponseEntity<Unit> {
        commandGateway.send<Any?>(
            DepositMoneyCommand(
                bankAccountId = request.accountId,
                amountOfMoney = request.amount
            )
        )
        return ResponseEntity.ok().build()
    }

    @GetMapping("/account", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun balance(@RequestBody request: BalanceRequest): ResponseEntity<BalanceResponse> =
        queryGateway.query(
            AccountBalanceQuery(
                bankAccountId = request.accountId
            ),
            ResponseTypes.instanceOf(Long::class.java)
        ).let { amount -> ResponseEntity.ok(BalanceResponse(request.accountId, amount.get())) }
}

data class CreateAccountResponse(val accountId: String)
data class DepositRequest(val accountId: String, val amount: Long)
data class BalanceRequest(val accountId: String)
data class BalanceResponse(val accountId: String, val balance: Long)

@org.springframework.context.annotation.Configuration
class DynamoDbDemoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun dynamoClient(
        @Value("\${aws.dynamodb.endpoint}")
        dynamoEndpoint: String,
        @Value("\${aws.region}")
        region: String,
    ): DynamoDbClient {
        // Placeholder values required by dynamodb client
//        System.setProperty("aws.accessKeyId", "local")
//        System.setProperty("aws.secretAccessKey", "local")
//        System.setProperty("aws.sessionToken", "local")

        return DynamoDbClient.builder()
            .region(Region.regions().first { it.id() == region })
//            .endpointOverride(URI.create(dynamoEndpoint))
            .build()
    }
}
