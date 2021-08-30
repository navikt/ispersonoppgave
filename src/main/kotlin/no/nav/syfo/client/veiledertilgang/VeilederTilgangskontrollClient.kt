package no.nav.syfo.client.veiledertilgang

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.metric.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val azureAdV2Client: AzureAdV2Client,
    private val syfotilgangskontrollClientId: String,
    private val endpointUrl: String,
) {
    private val httpClient = httpClientDefault()

    private val tilgangskontrollPersonUrl: String

    init {
        tilgangskontrollPersonUrl = "$endpointUrl$TILGANGSKONTROLL_PERSON_PATH"
    }

    suspend fun hasAccessWithOBO(
        personIdentNumber: PersonIdentNumber,
        token: String,
        callId: String,
    ): Boolean {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(
            scopeClientId = syfotilgangskontrollClientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        try {
            val response: HttpResponse = httpClient.get(tilgangskontrollPersonUrl) {
                header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
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
        const val TILGANGSKONTROLL_PERSON_PATH = "/syfo-tilgangskontroll/api/tilgang/navident/person"
    }
}
