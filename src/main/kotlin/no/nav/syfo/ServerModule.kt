package no.nav.syfo

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.api.registerPodApi
import no.nav.syfo.api.registerPrometheusApi
import no.nav.syfo.auth.isInvalidToken
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.api.registerVeilederPersonOppgaveApi
import no.nav.syfo.util.*
import java.util.*

@KtorExperimentalAPI
fun Application.serverModule() {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
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

    val personOppgaveService = PersonOppgaveService(database)
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
