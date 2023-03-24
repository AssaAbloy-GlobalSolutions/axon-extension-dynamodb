package assaabloy.globalsolutions.axon.extension.dynamodb.springboot.autoconfig

import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

// note that properties must be nullable var:s in order
// for spring boot to identify it as a java bean
@ConfigurationProperties("axon.dynamo")
@Validated
data class AxonDynamoProperties(
    @get:NotNull
    var eventPayloadPackagePrefix: String?,

    @get:NotNull
    var axonStorageTableName: String?,

    @get:NotNull
    var claimTimeout: Duration?,
)