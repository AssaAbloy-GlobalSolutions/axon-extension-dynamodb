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

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.axonframework.config.Configuration
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.bind.annotation.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.concurrent.TimeUnit

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

    @Autowired
    fun configure(config: EventProcessingConfigurer) {
        config.registerTrackingEventProcessor(
            "BalanceProjection",
            Configuration::eventStore
        ) {
            TrackingEventProcessorConfiguration
                .forParallelProcessing(4)
                .andBatchSize(100)
                // This will cause axon to extend claim every 5 seconds when there are no events coming in
                .andEventAvailabilityTimeout(5, TimeUnit.SECONDS)
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun dynamoClient(
        @Value("\${aws.region}")
        region: String,
    ): DynamoDbClient = DynamoDbClient.builder()
        .region(Region.regions().first { it.id() == region })
        .build()
}

data class CreateAccountResponse(val accountId: String)
data class DepositRequest(val accountId: String, val amount: Long)
data class BalanceRequest(val accountId: String)
data class BalanceResponse(val accountId: String, val balance: Long)
