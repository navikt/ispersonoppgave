package no.nav.syfo.personoppgave.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.*
import no.nav.syfo.auth.getTokenFromCookie
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.toPersonOppgaveVeileder
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

fun Route.registerVeilederPersonOppgaveApi(
    personOppgaveService: PersonOppgaveService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient
) {
    route("/api/v1/personoppgave") {
        get("/personident") {
            try {
                val callId = getCallId()
                val token = getTokenFromCookie(call.request.cookies)

                val personIdent = call.request.headers[NAV_PERSONIDENT_HEADER.toLowerCase()]
                    ?: throw IllegalArgumentException("No PersonIdent supplied")
                val fnr = Fodselsnummer(personIdent)

                when (veilederTilgangskontrollClient.hasAccess(fnr, token, callId)) {
                    true -> {
                        val personOppaveList = personOppgaveService.getPersonOppgaveList(fnr).map {
                            it.toPersonOppgaveVeileder()
                        }
                        when {
                            personOppaveList.isNotEmpty() -> call.respond(personOppaveList)
                            else -> call.respond(HttpStatusCode.NoContent)
                        }
                    }
                    else -> {
                        val accessDeniedMessage = "Denied Veileder access to PersonIdent with Fodselsnummer"
                        log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                        call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                    }
                }
            } catch (e: IllegalArgumentException) {
                val illegalArgumentMessage = "Could not retrieve PersonOppgaveList for PersonIdent"
                log.warn("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
