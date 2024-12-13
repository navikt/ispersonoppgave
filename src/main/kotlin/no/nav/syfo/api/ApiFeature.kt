package no.nav.syfo.api

import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import no.nav.syfo.metric.METRICS_REGISTRY
import no.nav.syfo.util.*
import java.time.Duration
import java.util.*

fun Application.installCallId() {
    install(CallId) {
        retrieve { it.request.headers[NAV_CALL_ID_HEADER] }
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }
}

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson { configure() }
    }
}

fun Application.installMetrics() {
    install(MicrometerMetrics) {
        registry = METRICS_REGISTRY
        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .maximumExpectedValue(Duration.ofSeconds(20).toNanos().toDouble())
            .build()
    }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val responseStatus: HttpStatusCode = when (cause) {
                is ResponseException -> {
                    cause.response.status
                }
                is IllegalArgumentException -> {
                    HttpStatusCode.BadRequest
                }
                else -> {
                    HttpStatusCode.InternalServerError
                }
            }

            val callId = call.getCallId()
            val consumerClientId = call.getConsumerId()
            call.application.log.error("Caught exception, callId=$callId, consumerClientId=$consumerClientId", cause)

            val message = cause.message ?: "Unknown error"
            call.respond(responseStatus, message)
        }
    }
}
