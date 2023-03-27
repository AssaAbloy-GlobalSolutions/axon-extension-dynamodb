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

package assaabloy.globalsolutions.axon.extension.dynamodb.example

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI


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

//    @Autowired
//    fun initializeTables(dynamoDbClient: DynamoDbClient) {
//        DynamoTableInitializer.initTables(dynamoDbClient)
//    }

    @Bean
    fun registerKotlinModule(): Module = KotlinModule
        .Builder()
        .configure(KotlinFeature.SingletonSupport, true)
        .build()

    @Bean
    fun registerJavaTimeModule(): Module = JavaTimeModule()

}

@org.springframework.context.annotation.Configuration
class DynamoDbDemoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun dynamoClient(
        @Value("\${amazon.dynamodb.endpoint}")
        dynamoEndpoint: String,
        @Value("\${aws.region}")
        region: String,
    ): DynamoDbClient {
        // Placeholder values required by dynamodb client
        System.setProperty("aws.accessKeyId", "local")
        System.setProperty("aws.secretAccessKey", "local")
        System.setProperty("aws.sessionToken", "local")

        return DynamoDbClient.builder()
            .region(Region.regions().first { it.id() == region })
            .endpointOverride(URI.create(dynamoEndpoint))
            .build()
    }
}
