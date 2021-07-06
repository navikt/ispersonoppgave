package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.syfo.api.apiModule
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.client.sts.StsRestClient
import no.nav.syfo.kafka.kafkaProducerConfig
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.retry.KOversikthendelseRetry
import no.nav.syfo.oversikthendelse.retry.OversikthendelseRetryProducer
import no.nav.syfo.util.getFileAsString
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(
    var running: Boolean = true,
    var initialized: Boolean = false
)

val log: org.slf4j.Logger = LoggerFactory.getLogger("no.nav.syfo.MainApplicationKt")

val backgroundTasksContext = Executors.newFixedThreadPool(6).asCoroutineDispatcher() + MDCContext()

@KtorExperimentalAPI
fun main() {
    val server = embeddedServer(
        Netty,
        applicationEngineEnvironment {
            log = LoggerFactory.getLogger("ktor.application")
            config = HoconApplicationConfig(ConfigFactory.load())

            connector {
                port = env.applicationPort
            }

            val vaultSecrets = VaultSecrets(
                serviceuserUsername = getFileAsString("/secrets/serviceuser/username"),
                serviceuserPassword = getFileAsString("/secrets/serviceuser/password")
            )

            val stsClientRest = StsRestClient(
                env.stsRestUrl,
                vaultSecrets.serviceuserUsername,
                vaultSecrets.serviceuserPassword
            )
            val behandlendeEnhetClient = BehandlendeEnhetClient(
                env.behandlendeenhetUrl,
                stsClientRest
            )
            val producerProperties = kafkaProducerConfig(env, vaultSecrets)
            val oversikthendelseRecordProducer = KafkaProducer<String, KOversikthendelse>(producerProperties)
            val oversikthendelseProducer = OversikthendelseProducer(oversikthendelseRecordProducer)

            val oversikthendelseRetryProducerProperties = kafkaProducerConfig(env, vaultSecrets)
            val oversikthendelseRetryRecordProducer = KafkaProducer<String, KOversikthendelseRetry>(oversikthendelseRetryProducerProperties)
            val oversikthendelseRetryProducer = OversikthendelseRetryProducer(oversikthendelseRetryRecordProducer)

            module {
                databaseModule()
                apiModule(
                    behandlendeEnhetClient,
                    oversikthendelseProducer
                )
                kafkaModule(
                    vaultSecrets,
                    behandlendeEnhetClient,
                    oversikthendelseProducer,
                    oversikthendelseRetryProducer
                )
            }
        }
    )
    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = false)
}

val state: ApplicationState = ApplicationState(running = false, initialized = false)
val env: Environment = getEnvironment()

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}

fun isPreProd(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "dev-fss") == "dev-fss"
