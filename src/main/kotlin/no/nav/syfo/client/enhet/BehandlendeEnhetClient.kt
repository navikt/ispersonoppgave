package no.nav.syfo.client.enhet

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.*
import no.nav.syfo.client.sts.StsRestClient
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.metric.*
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class BehandlendeEnhetClient(
    private val baseUrl: String,
    private val stsRestClient: StsRestClient
) {
    fun getEnhet(fnr: Fodselsnummer, callId: String): BehandlendeEnhet? {
        val bearer = stsRestClient.token()

        val (_, response, result) = getBehandlendeEnhetUrl(fnr).httpGet()
            .header(
                mapOf(
                    HttpHeaders.Authorization to bearerHeader(bearer),
                    HttpHeaders.Accept to "application/json",
                    NAV_CALL_ID to callId,
                    NAV_CONSUMER_ID to APP_CONSUMER_ID
                )
            )
            .responseString()

        result.fold(
            success = {
                return if (response.statusCode == 204) {
                    COUNT_CALL_BEHANDLENDEENHET_EMPTY.inc()
                    null
                } else {
                    val behandlendeEnhet = objectMapper.readValue<BehandlendeEnhet>(result.get())
                    if (isValid(behandlendeEnhet)) {
                        COUNT_CALL_BEHANDLENDEENHET_SUCCESS.inc()
                        behandlendeEnhet
                    } else {
                        COUNT_CALL_BEHANDLENDEENHET_FAIL.inc()
                        LOG.error("Error while requesting behandlendeenhet from syfobehandlendeenhet: Received invalid EnhetId with more than 4 chars for EnhetId {}", behandlendeEnhet.enhetId)
                        null
                    }
                }
            },
            failure = {
                COUNT_CALL_BEHANDLENDEENHET_FAIL.inc()
                val exception = it.exception
                LOG.error("Error with responseCode=${response.statusCode} with callId=$callId while requesting behandlendeenhet from syfobehandlendeenhet: ${exception.message}", exception)
                return null
            }
        )
    }

    private fun getBehandlendeEnhetUrl(brukerFnr: Fodselsnummer): String {
        return "$baseUrl/api/${brukerFnr.value}"
    }

    private fun isValid(behandlendeEnhet: BehandlendeEnhet): Boolean {
        return behandlendeEnhet.enhetId.length <= 4
    }

    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(BehandlendeEnhetClient::class.java)
    }
}
