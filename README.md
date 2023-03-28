## Axon DynamoDB Extension

The Axon Framework DynamoDB Extension extends the Axon Framework for building event-driven microservice systems.
Based on Domain Driven Design, CQRS, and Event Sourcing principles, this extension provides essential building
blocks such as Aggregate factories, Repositories, Command, Event and Query Buses, and an Event Store.

This DynamoDB extension streamlines infrastructure management, letting you concentrate on your business
functionality. The repository adds an extension to the Axon Framework that uses DynamoDB for persistence,
serving as an EventStorageEngine (for use with an EventStore) and TokenStore. Enjoy a well-structured
application with the Axon Framework DynamoDB Extension.


## Usage

### Prerequisites

This extension require Spring Boot 3, and therefore a java 17 runtime.

### Dependencies

To use the Axon DynamoDB Extension, add the following dependency to your project.

Maven:

```xml
<dependency>
    <groupId>assaabloy.globalsolutions.axon.dynamodb</groupId>
    <artifactId>axon-dynamodb-spring-boot-autoconfigure</artifactId>
    <version>${axon-dynamodb.version}</version>
</dependency>
```

Or with gradle:

```groovy
implementation 'assaabloy.globalsolutions.axon.dynamodb:axon-dynamodb-spring-boot-autoconfigure:${project.version}'
```


### Configuring the DynamoDB Client

To configure the DynamoDB client in your Spring Boot application, add the following bean definition:

```kotlin
@Bean
fun dynamoClient(
    @Value("\${amazon.dynamodb.endpoint}")
    dynamoEndpoint: String,
    @Value("\${amazon.aws.region}")
    region: String,
): DynamoDbClient {
    // Placeholder values for connecting to a locally running dynamodb container; 
    // these properties are typically set with `-Daws.accessKeyId=... -DsecretAccessKey=...`
    System.setProperty("aws.accessKeyId", "kid")
    System.setProperty("aws.secretAccessKey", "sak")
    System.setProperty("aws.sessionToken", "st")

    return DynamoDbClient.builder()
        .region(Region.regions().first { it.id() == region })
        .endpointOverride(URI.create(dynamoEndpoint))
        .build()
}
```


### Creating DynamoDB Tables

This extension does not create its own DynamoDB tables. You will need to handle the creation of
the required tables in your application. Refer to the test class DynamoTableInitializer for an
example of how to create the tables.

## License
This work is licensed under MIT License except the example project, which has been sourced from [axon-mongo-example][axon-mongo-example]
and falls under the Apache License 2.0. These files can be identified by the Apache License header. Apache 2.0 license
can be found under [axon-example/LICENSE.axon](axon-example/LICENSE.axon).

 [axon-mongo-example]: https://github.com/AxonFramework/extension-mongo
