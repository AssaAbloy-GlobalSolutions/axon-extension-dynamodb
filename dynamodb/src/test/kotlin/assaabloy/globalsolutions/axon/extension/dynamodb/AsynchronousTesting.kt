package assaabloy.globalsolutions.axon.extension.dynamodb

import mu.KotlinLogging
import java.util.*

private val kLogger = KotlinLogging.logger {}

fun uuid() = UUID.randomUUID().toString()

fun <T> assertEventually(
    retryLimit: Int = 10,
    sleepForMillis: Long = 100,
    successMessage: String? = null,
    function: () -> T
): T {
    var retries = 0

    while (retries <= retryLimit) {
        try {
            val response = function()

            val message = successMessage ?: resolveCallee()
            kLogger.debug("$message - success on retry $retries/$retryLimit ($sleepForMillis ms sleep timer)")
            return response
        } catch (e: Throwable) {
            retries++
            if (retries > retryLimit) {
                throw e
            }

            Thread.sleep(sleepForMillis)
        }
    }

    throw AssertionError("retryLimit must not be negative")
}

fun assertEventually(
    retryLimit: Int = 10,
    sleepForMillis: Long = 100,
    successMessage: String? = null,
    function: () -> Unit
) = assertEventually<Unit>(retryLimit, sleepForMillis, successMessage, function)

fun assertContinuously(limit: Int = 10, sleepForMillis: Long = 100, function: () -> Unit) {
    for (i in 0..limit) {
        function()
        Thread.sleep(sleepForMillis)
    }
}

private fun resolveCallee(): String = Throwable()
    .stackTrace
    .first { "AsynchronousTesting.kt" != it.fileName }
    .let { "${it.className.substringAfterLast(".")}.${it.methodName}(${it.fileName}:${it.lineNumber})" }