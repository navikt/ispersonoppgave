package no.nav.syfo.client.veiledertilgang

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.metric.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val azureAdClient: AzureAdClient,
    private val syfotilgangskontrollClientId: String,
    private val endpointUrl: String,
    private val httpClient: HttpClient = httpClientDefault(),
) {

    private val tilgangskontrollPersonUrl: String

    init {
        tilgangskontrollPersonUrl = "$endpointUrl$TILGANGSKONTROLL_PERSON_PATH"
    }

    suspend fun hasAccessWithOBO(
        personIdent: PersonIdent,
        token: String,
        callId: String,
    ): Boolean {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = syfotilgangskontrollClientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        try {
            val response: HttpResponse = httpClient.get(tilgangskontrollPersonUrl) {
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.increment()
            return response.body<Tilgang>().harTilgang
        } catch (e: ClientRequestException) {
            return if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN.increment()
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
        COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.increment()
        return false
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)
        const val TILGANGSKONTROLL_PERSON_PATH = "/syfo-tilgangskontroll/api/tilgang/navident/person"
    }
}
