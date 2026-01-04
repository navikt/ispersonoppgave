package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.api.authentication.getWellKnown
import no.nav.syfo.application.PersonOppgaveService
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.cronjob.cronjobModule
import no.nav.syfo.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.infrastructure.database.database
import no.nav.syfo.infrastructure.database.databaseModule
import no.nav.syfo.infrastructure.kafka.kafkaAivenProducerConfig
import no.nav.syfo.infrastructure.kafka.launchKafkaTasks
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.KPersonoppgavehendelse
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.PersonoppgavehendelseProducer
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
    lateinit var personOppgaveService: PersonOppgaveService

    val applicationEngineEnvironment = applicationEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
        config = HoconApplicationConfig(ConfigFactory.load())
    }
    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
        configure = {
            connector {
                port = applicationPort
            }
            connectionGroupSize = 8
            workerGroupSize = 8
            callGroupSize = 16
        },
        module = {
            databaseModule(
                environment = environment,
            )

            val personoppgaveRepository = PersonOppgaveRepository(database = database)
            personOppgaveService = PersonOppgaveService(
                database = database,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
                personoppgaveRepository = personoppgaveRepository,
            )

            apiModule(
                applicationState = applicationState,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                database = database,
                environment = environment,
                personOppgaveService = personOppgaveService,
                wellKnownInternADV2 = wellKnownInternADV2,
            )
            cronjobModule(
                applicationState = applicationState,
                database = database,
                environment = environment,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
                personOppgaveRepository = personoppgaveRepository,
            )
            monitor.subscribe(ApplicationStarted) {
                applicationState.ready = true
                log.info("Application is ready, running Java VM ${Runtime.version()}")
                launchKafkaTasks(
                    applicationState = applicationState,
                    database = database,
                    environment = environment,
                    personoppgavehendelseProducer = personoppgavehendelseProducer,
                    pdlClient = pdlClient,
                )
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, timeUnit = TimeUnit.SECONDS)
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
