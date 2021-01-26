package no.nav.syfo.client.veiledertilgang

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.metric.COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL
import no.nav.syfo.metric.COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN
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
        try {
            val response: HttpResponse = httpClient.get(getTilgangskontrollUrl(fnr)) {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.inc()
            return response.receive<Tilgang>().harTilgang
        } catch (e: ClientRequestException) {
            return if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN.inc()
                false
            } else {
                return handleUnexpectedReponseException(e.response)
            }
        } catch (e: ServerResponseException) {
            return handleUnexpectedReponseException(e.response)
        }
    }

    private fun getTilgangskontrollUrl(brukerFnr: Fodselsnummer): String {
        return "$endpointUrl/syfo-tilgangskontroll/api/tilgang/bruker?fnr=${brukerFnr.value}"
    }

    private fun handleUnexpectedReponseException(response: HttpResponse): Boolean {
        log.error(
            "Error while requesting access to person from syfo-tilgangskontroll with {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString())
        )
        COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.inc()
        return false
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)
    }
}
