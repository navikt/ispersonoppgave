package no.nav.syfo

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.HoconApplicationConfig
import io.ktor.features.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.syfo.api.registerPodApi
import no.nav.syfo.api.registerPrometheusApi
import no.nav.syfo.kafka.setupKafka
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(
    var running: Boolean = true,
    var initialized: Boolean = false
)

val log: org.slf4j.Logger = LoggerFactory.getLogger("no.nav.syfo.MainApplicationKt")

val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher() + MDCContext()

@KtorExperimentalAPI
fun main() {
    val vaultSecrets = VaultSecrets(
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username"),
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password")
    )

    val server = embeddedServer(Netty, applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
        config = HoconApplicationConfig(ConfigFactory.load())

        connector {
            port = env.applicationPort
        }

        module {
            databaseModule()
            serverModule()
            kafkaModule(vaultSecrets)
        }
    })
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(10, 10, TimeUnit.SECONDS)
    })

    server.start(wait = false)
}

val state: ApplicationState = ApplicationState(running = false, initialized = false)
val env: Environment = getEnvironment()

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
    }

    routing {
        registerPodApi(state)
        registerPrometheusApi()
    }

    state.initialized = true
}

fun Application.kafkaModule(
    vaultSecrets: VaultSecrets
) {
    launch(backgroundTasksContext) {
        setupKafka(
            vaultSecrets
        )
    }
}

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
