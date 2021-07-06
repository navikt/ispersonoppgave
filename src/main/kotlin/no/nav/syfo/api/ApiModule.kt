package no.nav.syfo.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import no.nav.syfo.auth.isInvalidToken
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.database.database
import no.nav.syfo.env
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.api.registerVeilederPersonOppgaveApi
import no.nav.syfo.state

@KtorExperimentalAPI
fun Application.apiModule(
    behandlendeEnhetClient: BehandlendeEnhetClient,
    oversikthendelseProducer: OversikthendelseProducer
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
        database,
        behandlendeEnhetClient,
        oversikthendelseProducer
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(env.syfotilgangskontrollUrl)

    routing {
        registerPodApi(state)
        registerPrometheusApi()
        registerVeilederPersonOppgaveApi(
            personOppgaveService,
            veilederTilgangskontrollClient
        )
    }

    state.initialized = true
}
