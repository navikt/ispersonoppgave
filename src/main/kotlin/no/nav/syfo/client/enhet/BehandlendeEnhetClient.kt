package no.nav.syfo.client.enhet

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.sts.StsRestClient
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.metric.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class BehandlendeEnhetClient(
    private val baseUrl: String,
    private val stsRestClient: StsRestClient,
) {
    private val client = httpClientDefault()

    suspend fun getEnhet(fnr: Fodselsnummer, callId: String): BehandlendeEnhet? {
        val bearer = stsRestClient.token()

        val response: HttpResponse = client.get(getBehandlendeEnhetUrl(fnr)) {
            header(HttpHeaders.Authorization, bearerHeader(bearer))
            header(NAV_CALL_ID, callId)
            header(NAV_CONSUMER_ID, APP_CONSUMER_ID)
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
                    LOG.error("Error while requesting behandlendeenhet from syfobehandlendeenhet: Received invalid EnhetId with more than 4 chars for EnhetId {}", behandlendeEnhet.enhetId)
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
    }

    private fun getBehandlendeEnhetUrl(brukerFnr: Fodselsnummer): String {
        return "$baseUrl/api/${brukerFnr.value}"
    }

    private fun isValid(behandlendeEnhet: BehandlendeEnhet): Boolean {
        return behandlendeEnhet.enhetId.length <= 4
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(BehandlendeEnhetClient::class.java)
    }
}
