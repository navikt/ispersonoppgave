package no.nav.syfo.personoppgave.api.v2

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.auth.getNAVIdentFromToken
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.toPersonOppgaveVeileder
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val registerVeilederPersonOppgaveApiV2BasePath = "/api/v2/personoppgave"

fun Route.registerVeilederPersonOppgaveApiV2(
    personOppgaveService: PersonOppgaveService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(registerVeilederPersonOppgaveApiV2BasePath) {
        get("/personident") {
            val callId = getCallId()
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("Could not retrieve PersonOppgaveList for PersonIdent: No Authorization header supplied")

            val personIdent = personIdentHeader()
                ?: throw IllegalArgumentException("Could not retrieve PersonOppgaveList for PersonIdent: No PersonIdent supplied")
            val fnr = PersonIdentNumber(personIdent)

            if (veilederTilgangskontrollClient.hasAccessWithOBO(fnr, token, callId)) {
                val personOppaveList = personOppgaveService.getPersonOppgaveList(fnr).map {
                    it.toPersonOppgaveVeileder()
                }
                if (personOppaveList.isNotEmpty()) {
                    call.respond(personOppaveList)
                } else call.respond(HttpStatusCode.NoContent)
            } else {
                val accessDeniedMessage = "Denied Veileder access to PersonIdent with Fodselsnummer"
                log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
            }
        }

        post("/{uuid}/behandle") {
            val callId = getCallId()
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("Error while processing of PersonOppgave for PersonIdent for navIdent: No Authorization header supplied")

            val uuid: UUID = UUID.fromString(call.parameters["uuid"])

            val personoppgave = personOppgaveService.getPersonOppgave(uuid)
            personoppgave?.let {
                if (personoppgave.behandletTidspunkt != null) {
                    call.respond(HttpStatusCode.Conflict)
                } else {
                    if (veilederTilgangskontrollClient.hasAccessWithOBO(
                            personoppgave.personIdentNumber,
                            token,
                            callId
                        )
                    ) {
                        val navIdent = getNAVIdentFromToken(token)
                        personOppgaveService.behandlePersonOppgave(personoppgave, navIdent, callId)
                        call.respond(HttpStatusCode.OK)
                    } else {
                        val accessDeniedMessage = "Denied Veileder access to PersonOppgave for PersonIdent with Fodselsnummer"
                        log.warn("$accessDeniedMessage, {}", callIdArgument(callId))
                        call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
                    }
                }
            } ?: throw IllegalArgumentException("Error while processing of PersonOppgave for PersonIdent for navIdent: No PersonOppgave was found for uuid")
        }
    }
}
