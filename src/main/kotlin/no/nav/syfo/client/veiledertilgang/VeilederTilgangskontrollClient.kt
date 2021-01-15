package no.nav.syfo.client.veiledertilgang

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.metric.COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL
import no.nav.syfo.metric.COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS
import no.nav.syfo.util.NAV_CALL_ID
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val endpointUrl: String
) {
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    suspend fun hasAccess(
        fnr: Fodselsnummer,
        token: String,
        callId: String
    ): Boolean {
        val response: HttpResponse = httpClient.get(getTilgangskontrollUrl(fnr)) {
            header(HttpHeaders.Authorization, bearerHeader(token))
            header(NAV_CALL_ID, callId)
            accept(ContentType.Application.Json)
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.inc()
                response.receive<Tilgang>().harTilgang
            }
            else -> {
                val statusCode: String = response.status.value.toString()
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.labels(statusCode).inc()
                log.info("Error while requesting access to person from syfo-tilgangskontroll with status=${response.status}")
                false
            }
        }
    }

    private fun getTilgangskontrollUrl(brukerFnr: Fodselsnummer): String {
        return "$endpointUrl/syfo-tilgangskontroll/api/tilgang/bruker?fnr=${brukerFnr.value}"
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)
    }
}
