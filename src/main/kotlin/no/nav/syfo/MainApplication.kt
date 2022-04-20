package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.api.authentication.getWellKnown
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.database.database
import no.nav.syfo.database.databaseModule
import no.nav.syfo.kafka.kafkaProducerConfig
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.retry.KOversikthendelseRetry
import no.nav.syfo.oversikthendelse.retry.OversikthendelseRetryProducer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = false
)

val log: org.slf4j.Logger = LoggerFactory.getLogger("no.nav.syfo.MainApplicationKt")

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val environment = Environment()

    val azureAdClient = AzureAdV2Client(
        azureAppClientId = environment.azureAppClientId,
        azureAppClientSecret = environment.azureAppClientSecret,
        azureTokenEndpoint = environment.azureTokenEndpoint,
    )

    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.behandlendeenhetUrl,
        syfobehandlendeenhetClientId = environment.syfobehandlendeenhetClientId,
    )
    val producerProperties = kafkaProducerConfig(env = environment)
    val oversikthendelseRecordProducer = KafkaProducer<String, KOversikthendelse>(producerProperties)
    val oversikthendelseProducer = OversikthendelseProducer(oversikthendelseRecordProducer)

    val oversikthendelseRetryProducerProperties = kafkaProducerConfig(env = environment)
    val oversikthendelseRetryRecordProducer =
        KafkaProducer<String, KOversikthendelseRetry>(oversikthendelseRetryProducerProperties)
    val oversikthendelseRetryProducer = OversikthendelseRetryProducer(oversikthendelseRetryRecordProducer)

    val wellKnownInternADV2 = getWellKnown(
        wellKnownUrl = environment.azureAppWellKnownUrl,
    )

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
        config = HoconApplicationConfig(ConfigFactory.load())
        connector {
            port = applicationPort
        }

        module {
            databaseModule(
                environment = environment,
            )
            apiModule(
                applicationState = applicationState,
                behandlendeEnhetClient = behandlendeEnhetClient,
                database = database,
                environment = environment,
                oversikthendelseProducer = oversikthendelseProducer,
                wellKnownInternADV2 = wellKnownInternADV2,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) { application ->
        applicationState.ready = true
        application.environment.log.info("Application is ready")
        if (environment.toggleKafkaConsumerEnabled) {
            launchKafkaTasks(
                applicationState = applicationState,
                database = database,
                environment = environment,
                behandlendeEnhetClient = behandlendeEnhetClient,
                oversikthendelseProducer = oversikthendelseProducer,
                oversikthendelseRetryProducer = oversikthendelseRetryProducer,
            )
        }
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(
                gracePeriod = 10,
                timeout = 10,
                timeUnit = TimeUnit.SECONDS,
            )
        }
    )

    server.start(wait = false)
}

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
