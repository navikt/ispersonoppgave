package no.nav.syfo.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.auth.isInvalidToken
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.api.registerVeilederPersonOppgaveApi

fun Application.apiModule(
    applicationState: ApplicationState,
    behandlendeEnhetClient: BehandlendeEnhetClient,
    database: DatabaseInterface,
    environment: Environment,
    oversikthendelseProducer: OversikthendelseProducer,
) {
    installCallId()
    installContentNegotiation()
    installStatusPages()

    intercept(ApplicationCallPipeline.Call) {
        if (call.request.uri.contains(Regex("is_alive|is_ready|prometheus"))) {
            proceed()
            return@intercept
        }
        val cookies = call.request.cookies
        if (isInvalidToken(cookies)) {
            call.respond(HttpStatusCode.Unauthorized, "Ugyldig token")
            finish()
        } else {
            proceed()
        }
    }

    val personOppgaveService = PersonOppgaveService(
        database = database,
        behandlendeEnhetClient = behandlendeEnhetClient,
        oversikthendelseProducer = oversikthendelseProducer,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        endpointUrl = environment.syfotilgangskontrollUrl,
    )

    routing {
        registerPodApi(applicationState)
        registerPrometheusApi()
        registerVeilederPersonOppgaveApi(
            personOppgaveService,
            veilederTilgangskontrollClient
        )
    }

    applicationState.ready = true
}
