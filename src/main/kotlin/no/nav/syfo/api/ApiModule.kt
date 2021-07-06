package no.nav.syfo.api

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import no.nav.syfo.*
import no.nav.syfo.auth.isInvalidToken
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.database.database
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.api.registerVeilederPersonOppgaveApi
import no.nav.syfo.util.*
import java.util.*

@KtorExperimentalAPI
fun Application.apiModule(
    behandlendeEnhetClient: BehandlendeEnhetClient,
    oversikthendelseProducer: OversikthendelseProducer
) {
    install(ContentNegotiation) {
        jackson(block = configureJacksonMapper())
    }

    install(CallId) {
        retrieve { it.request.headers["X-Nav-CallId"] }
        retrieve { it.request.headers[HttpHeaders.XCorrelationId] }
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }

    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            log.error("Caught exception", cause, getCallId(), getConsumerId())
            throw cause
        }
    }

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
