package no.nav.syfo.client.enhet

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.metric.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class BehandlendeEnhetClient(
    private val azureAdClient: AzureAdV2Client,
    baseUrl: String,
    private val syfobehandlendeenhetClientId: String,
) {
    private val client = httpClientDefault()

    private val behandlendeEnhetUrl = "$baseUrl$BEHANDLENDEENHET_PATH"

    suspend fun getEnhet(
        personIdentNumber: PersonIdentNumber,
        callId: String
    ): BehandlendeEnhet? {
        val bearer = azureAdClient.getSystemToken(
            scopeClientId = syfobehandlendeenhetClientId,
        )?.accessToken
            ?: throw RuntimeException("Failed to request access to BehandlendeEnhet: Failed to get AzureAD token")

        try {
            val response: HttpResponse = client.get(behandlendeEnhetUrl) {
                header(HttpHeaders.Authorization, bearerHeader(bearer))
                header(NAV_CALL_ID, callId)
                header(NAV_CONSUMER_ID, APP_CONSUMER_ID)
                header(NAV_PERSONIDENT_HEADER, personIdentNumber.value)
                accept(ContentType.Application.Json)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val behandlendeEnhet = response.receive<BehandlendeEnhet>()
                    return if (isValid(behandlendeEnhet)) {
                        COUNT_CALL_BEHANDLENDEENHET_SUCCESS.inc()
                        behandlendeEnhet
                    } else {
                        COUNT_CALL_BEHANDLENDEENHET_FAIL.inc()
                        LOG.error(
                            "Error while requesting behandlendeenhet from syfobehandlendeenhet: Received invalid EnhetId with more than 4 chars for EnhetId {}",
                            behandlendeEnhet.enhetId
                        )
                        null
                    }
                }
                HttpStatusCode.NoContent -> {
                    LOG.error("BehandlendeEnhet returned HTTP-${response.status.value}: No BehandlendeEnhet was found for Fodselsnummer")
                    COUNT_CALL_BEHANDLENDEENHET_EMPTY.inc()
                    return null
                }
                else -> {
                    COUNT_CALL_BEHANDLENDEENHET_FAIL.inc()
                    LOG.error("Error with responseCode=${response.status.value} with callId=$callId while requesting behandlendeenhet from syfobehandlendeenhet")
                    return null
                }
            }
        } catch (responseException: ResponseException) {
            COUNT_CALL_BEHANDLENDEENHET_FAIL.inc()
            LOG.error("Error with responseCode=${responseException.response.status.value} with callId=$callId while requesting behandlendeenhet from syfobehandlendeenhet")
            return null
        }
    }

    private fun isValid(behandlendeEnhet: BehandlendeEnhet): Boolean {
        return behandlendeEnhet.enhetId.length <= 4
    }

    companion object {
        const val BEHANDLENDEENHET_PATH = "/api/system/v2/personident"

        private val LOG = LoggerFactory.getLogger(BehandlendeEnhetClient::class.java)
    }
}
