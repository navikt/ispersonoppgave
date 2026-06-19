package no.nav.syfo.api.v2

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.PersonOppgaveService
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.isBehandlet
import no.nav.syfo.domain.toPersonOppgaveVeileder
import java.util.*

const val registerVeilederPersonOppgaveApiV2BasePath = "/api/v2/personoppgave"

fun Route.registerVeilederPersonOppgaveApiV2(
    personOppgaveService: PersonOppgaveService,
    tilgangskontrollClient: TilgangskontrollClient,
) {
    route(registerVeilederPersonOppgaveApiV2BasePath) {
        get("/personident") {
            checkPersonAndSyfoTilgang(
                action = "Get personoppgaver for personident",
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = false,
            ) { _, targetPersonident, _ ->
                val fnr = PersonIdent(targetPersonident.value)

                val personoppgaver = personOppgaveService.getPersonOppgaveList(fnr).map {
                    it.toPersonOppgaveVeileder()
                }
                if (personoppgaver.isNotEmpty()) {
                    call.respond(personoppgaver)
                } else call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/{uuid}/behandle") {
            val uuid: UUID = UUID.fromString(call.parameters["uuid"])

            val personOppgave = requireNotNull(personOppgaveService.getPersonOppgave(uuid)) {
                "Failed to behandle pesonoppgave: No PersonOppgave was found for uuid $uuid"
            }
            val personOppgavePersonident = Personident(personOppgave.personIdent.value)

            checkPersonAndSyfoTilgang(
                action = "Behandle personoppgave with uuid $uuid",
                personident = personOppgavePersonident,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, _, _ ->
                personOppgave.let {
                    if (personOppgave.isBehandlet()) {
                        call.respond(HttpStatusCode.Conflict)
                    } else {
                        val navident = authorizedUser.navident.value
                        personOppgaveService.behandlePersonOppgave(personOppgave, navident)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        post("/behandle") {
            val requestDTO = call.receive<BehandlePersonoppgaveRequestDTO>()
            val personidentFromBody = Personident(requestDTO.personIdent)

            checkPersonAndSyfoTilgang(
                action = "Behandle personoppgaver for personident of type",
                personident = personidentFromBody,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, targetPersonident, _ ->
                val personoppgaver = personOppgaveService.getUbehandledePersonOppgaver(
                    personIdent = PersonIdent(targetPersonident.value),
                    personOppgaveType = requestDTO.personOppgaveType,
                )

                if (personoppgaver.isEmpty()) {
                    call.respond(HttpStatusCode.Conflict)
                } else {
                    val navident = authorizedUser.navident.value
                    personOppgaveService.behandlePersonOppgaver(personoppgaver, navident)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
