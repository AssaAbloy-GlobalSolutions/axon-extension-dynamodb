FROM base-alpine-java as build
COPY axon-dynamodb-example/target/axon-dynamodb-example.jar /
RUN jar -xf axon-dynamodb-example.jar

FROM base-alpine-java
COPY --from=build BOOT-INF/lib /app/lib
COPY --from=build META-INF /app/META-INF
COPY --from=build BOOT-INF/classes /app

EXPOSE 8080
EXPOSE 8090

RUN adduser -D axon-dynamodb
USER axon-dynamodb

# Workaround to disable netty to not use native: https://github.com/micrometer-metrics/micrometer/issues/2776#issuecomment-919759113
CMD ["java", "-Dio.micrometer.shaded.io.netty.transport.noNative=true", "-cp", "app:app/lib/*", "com.assaabloyglobalsolutions.axon.extension.dynamodb.example.DynamoDbAxonExampleApplicationKt"]

