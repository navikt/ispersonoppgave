package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.api.authentication.getWellKnown
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.cronjob.cronjobModule
import no.nav.syfo.database.database
import no.nav.syfo.database.databaseModule
import no.nav.syfo.kafka.kafkaAivenProducerConfig
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
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

    val producerProperties = kafkaAivenProducerConfig(environmentKafka = environment.kafka)
    val kafkaProducer = KafkaProducer<String, KPersonoppgavehendelse>(producerProperties)
    val personoppgavehendelseProducer = PersonoppgavehendelseProducer(kafkaProducer)

    val wellKnownInternADV2 = getWellKnown(
        wellKnownUrl = environment.azureAppWellKnownUrl,
    )

    val azureAdClient = AzureAdClient(
        azureAppClientId = environment.azureAppClientId,
        azureAppClientSecret = environment.azureAppClientSecret,
        azureTokenEndpoint = environment.azureTokenEndpoint,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        istilgangskontrollClientId = environment.istilgangskontrollClientId,
        endpointUrl = environment.istilgangskontrollUrl,
    )

    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlClientId = environment.pdlClientId,
        pdlUrl = environment.pdlUrl,
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
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                database = database,
                environment = environment,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
                wellKnownInternADV2 = wellKnownInternADV2,
            )
            cronjobModule(
                applicationState = applicationState,
                database = database,
                environment = environment,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) { application ->
        applicationState.ready = true
        application.environment.log.info("Application is ready, running Java VM ${Runtime.version()}")
        launchKafkaTasks(
            applicationState = applicationState,
            database = database,
            environment = environment,
            personoppgavehendelseProducer = personoppgavehendelseProducer,
            pdlClient = pdlClient,
        )
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    ) {
        connectionGroupSize = 8
        workerGroupSize = 8
        callGroupSize = 16
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(
                gracePeriod = 10,
                timeout = 10,
                timeUnit = TimeUnit.SECONDS,
            )
        }
    )

    server.start(wait = true)
}

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
