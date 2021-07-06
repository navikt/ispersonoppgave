package no.nav.syfo.personoppgave.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.*
import no.nav.syfo.auth.getTokenFromCookie
import no.nav.syfo.auth.getVeilederTokenPayload
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.toPersonOppgaveVeileder
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

fun Route.registerVeilederPersonOppgaveApi(
    personOppgaveService: PersonOppgaveService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route("/api/v1/personoppgave") {
        get("/personident") {
            try {
                val callId = getCallId()
                val token = getTokenFromCookie(call.request.cookies)

                val personIdent = call.request.headers[NAV_PERSONIDENT_HEADER.toLowerCase()]
                    ?: throw IllegalArgumentException("No PersonIdent supplied")
                val fnr = PersonIdentNumber(personIdent)

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

        post("/{uuid}/behandle") {
            try {
                val callId = getCallId()
                val token = getTokenFromCookie(call.request.cookies)

                val uuid: UUID = UUID.fromString(call.parameters["uuid"])

                when (val personoppgave = personOppgaveService.getPersonOppgave(uuid)) {
                    null -> {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                    else -> {
                        if (personoppgave.behandletTidspunkt != null) {
                            call.respond(HttpStatusCode.Conflict)
                        } else {
                            when (veilederTilgangskontrollClient.hasAccess(personoppgave.personIdentNumber, token, callId)) {
                                true -> {
                                    val navIdent = getVeilederTokenPayload(token).navIdent
                                    personOppgaveService.behandlePersonOppgave(personoppgave, navIdent, callId)
                                    call.respond(HttpStatusCode.OK)
                                }
                                else -> {
                                    val accessDeniedMessage = "Denied Veileder access to PersonOppgave for PersonIdent with Fodselsnummer"
                                    log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                                    call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                                }
                            }
                        }
                    }
                }
            } catch (e: IllegalArgumentException) {
                val navIdent = getVeilederTokenPayload(getTokenFromCookie(call.request.cookies)).navIdent
                val illegalArgumentMessage = "Error while processing of PersonOppgave for PersonIdent for navIdent=$navIdent"
                log.error("$illegalArgumentMessage: {}, {}", e.message, callIdArgument(getCallId()))
                call.respond(HttpStatusCode.BadRequest, e.message ?: illegalArgumentMessage)
            }
        }
    }
}
