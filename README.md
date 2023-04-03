## Axon Framework - DynamoDB Extension




[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.assaabloyglobalsolutions.axon.dynamodb/axon-dynamodb-parent/badge.png)](https://mvnrepository.com/artifact/com.assaabloyglobalsolutions.axon.dynamodb/axon-dynamodb-parent)

This repository adds an extension to the Axon Framework that uses DynamoDB for persistence, serving as
an `EventStorageEngine` (for use with an `EventStore`) and
a `TokenStore`. [Axon Framework](https://developer.axoniq.io/axon-framework/overview) is a framework for building
evolutionary, event-driven microservice systems based on the principles of Domain-Driven Design (DDD), Command-Query
Responsibility Separation (CQRS), and Event Sourcing.

At ASSA ABLOY Global Solutions we build cloud-based services to support our various business areas. We have been using
Event Sourcing and CQRS for a long time when building our services, and also the Axon Framework. We host all our
services in AWS and wanted a light-weight and AWS native solution to data storage. DynamoDB is not a perfect fit for an
event store, but we still see a lot of advantages with DynamoDB over e.g. an Aurora RDS instance. It is easier to
manage, it has built in autoscaling of both performance and storage, it is cheaper, etc. We have managed to come up with
an implementation using DynamoDB that we feel is stable and scalable enough for us to use internally, and hope others
might also benefit from this extension.

## Usage

We have added a *-spring-boot-autoconfigure module to help you getting started in a spring setup. The guide below will
assume you are using Spring. If you are not in a spring environment look at the code in `DynamoDbAutoConfiguration` for
guidance on how to manually create the `EventStorageEngine` and the `TokenStore`.

### Prerequisites

This extension require Spring Boot 3, and therefore a java 17 runtime.

### Dependencies

To use the Axon DynamoDB Extension, add the following dependency to your project.

Maven:

```xml
<dependency>
    <groupId>com.assaabloyglobalsolutions.axon.dynamodb</groupId>
    <artifactId>axon-dynamodb-spring-boot-autoconfigure</artifactId>
    <version>${axon-dynamodb.version}</version>
</dependency>
```

Or with gradle:

```groovy
implementation 'assaabloy.globalsolutions.axon.dynamodb:axon-dynamodb-spring-boot-autoconfigure:${axon-dynamodb.version}'
```

### Configuring a DynamoDB Client

In order for the extension to work it needs a DynamoDB client available as a spring bean. To configure the DynamoDB
client in your Spring Boot application, add the following bean definition:

```kotlin
@Bean
fun dynamoClient(): DynamoDbClient =
   DynamoDbClient.builder()
      // your configuration...
      .build()
```

### Extension properties

There are a few properties you can set to configure and customize the extension.

1. `axon.dynamo.axon-storage-table-name`
   - *Required*. Specifies the name of the table to use in DynamoDB. Both tokens and events are stored in the same
     table.
2. `axon.dynamo.claim-timeout`
   - *Optional, default: 10s*. How often the token store will check for claim timeouts. More details about the token
     claim mechanism can be found in the Axon documentation.
3. `axon.dynamo.event-payload-package-prefix`
   - *Optional, default: not used*. To save storage space (and cost) you can specify the packagename prefix where your
     events resides. When saving events the storage engine will replace the prefix with a `*`. Beware that this can
     cause issues (or save you) if you refactor your package structure.

### Creating DynamoDB Tables

This extension does not create its own DynamoDB tables. You will need to handle the creation of the required tables in
your application. Refer to the test class `DynamoTableInitializer` for an example of how to create the tables
programmatically. Another option is to use a CloudFormation template, or AWS CLI (there is a script creating dynamo
tables using the AWS CLI in the axon-dynamodb-example module) to create the required tables.

## Limitations

### Performance

DynamoDB is not perfect fit for an Event Store. There is a challenge with keeping track of the global event sequence as
Dynamo doesn't have support for sequences. When you have too many threads and services creating events at the same time
you will eventually run in to issues where many threads allocate global indexes for the events they are about to store,
and they commit their events out of order. The implementation will ensure that the global event sequence is maintained
without duplicates, but threads allocating and storing events out of order will create gaps in the global event
sequence. The Axon `EventStore` will handle these gaps gracefully, but you will eventually come to a point where
performance will degrade significantly because there are too many gaps to handle.

Make sure to test performance with your business cases to make sure the DynamoDB storage is handling the load you
expect.

### Functionality

So far we have only implemented support for handling Events. None of the services where we use this extension has the
need for snapshots, so there is not yet support for snapshots in the DynamoStorageEngine. Implementing support for
snapshots should however be fairly simple should you need them.

## License

This work is licensed under MIT License except the example project, which has been sourced
from [axon-mongo-example][axon-mongo-example]
and falls under the Apache License 2.0. These files can be identified by the Apache License header. Apache 2.0 license
can be found under [axon-example/LICENSE.axon](axon-example/LICENSE.axon).

[axon-mongo-example]: https://github.com/AxonFramework/extension-mongo
