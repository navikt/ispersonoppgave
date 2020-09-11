package no.nav.syfo.client.veiledertilgang

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.kittinunf.fuel.httpGet
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.metric.COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL
import no.nav.syfo.metric.COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.bearerHeader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.client.tilgangskontroll")

class VeilederTilgangskontrollClient(
    private val endpointUrl: String
) {
    fun hasAccess(
        fnr: Fodselsnummer,
        token: String,
        callId: String
    ): Boolean {
        val (_, _, result) = getTilgangskontrollUrl(fnr).httpGet()
            .header(mapOf(
                "Authorization" to bearerHeader(token),
                "Content-Type" to "application/json",
                NAV_CALL_ID_HEADER to callId
            ))
            .responseString()

        result.fold(
            success = {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.inc()
                val tilgang = objectMapper.readValue<Tilgang>(result.get())
                return tilgang.harTilgang
            },
            failure = {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL.inc()
                val exception = it.exception
                log.error("Error while requesting access to list of person from syfo-tilgangskontroll: ${exception.message}", exception)
                return false
            })
    }

    private fun getTilgangskontrollUrl(brukerFnr: Fodselsnummer): String {
        return "$endpointUrl/syfo-tilgangskontroll/api/tilgang/bruker?fnr=${brukerFnr.value}"
    }

    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
