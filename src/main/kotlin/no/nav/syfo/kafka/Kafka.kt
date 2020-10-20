package no.nav.syfo.kafka

import io.ktor.util.*
import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.*
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.oversikthendelse.retry.*
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.kafkaCallId
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

private val LOG: Logger = LoggerFactory.getLogger("no.nav.syfo.Kafka")

const val OPPFOLGINGSPLAN_LPS_NAV_TOPIC = "aapen-syfo-oppfolgingsplan-lps-nav-v1"

suspend fun CoroutineScope.setupKafka(
    vaultSecrets: VaultSecrets,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService,
    oversikthendelseRetryService: OversikthendelseRetryService,
    toggleProcessing: Boolean,
    toggleRetry: Boolean,
) {
    LOG.info("Setting up kafka consumer OppfolgingsplanLPS")

    launchListeners(
        state,
        kafkaConsumerConfig(env, vaultSecrets),
        oppfolgingsplanLPSService,
        toggleProcessing
    )

    LOG.info("Setting up kafka consumer OversikthendelseRetry")

    launchListenerOversikthendelseRetry(
        state,
        kafkaConsumerOversikthendelseRetryProperties(env, vaultSecrets),
        oversikthendelseRetryService,
        toggleRetry
    )

    state.initialized = true
}

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListeners(
    applicationState: ApplicationState,
    consumerProperties: Properties,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService,
    toggleProcessing: Boolean
) {
    val kafkaConsumerOppfolgingsplanLPSNAV = KafkaConsumer<String, KOppfolgingsplanLPSNAV>(consumerProperties)

    kafkaConsumerOppfolgingsplanLPSNAV.subscribe(
        listOf(OPPFOLGINGSPLAN_LPS_NAV_TOPIC)
    )

    createListener(applicationState) {
        blockingApplicationLogic(
            applicationState,
            kafkaConsumerOppfolgingsplanLPSNAV,
            oppfolgingsplanLPSService,
            toggleProcessing
        )
    }
}

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListenerOversikthendelseRetry(
    applicationState: ApplicationState,
    consumerOversikthendelseRetryProperties: Properties,
    oversikthendelseRetryService: OversikthendelseRetryService,
    toggleRetry: Boolean,
) {
    val kafkaConsumerOversikthendelseRetry = KafkaConsumer<String, String>(consumerOversikthendelseRetryProperties)

    kafkaConsumerOversikthendelseRetry.subscribe(
        listOf(OVERSIKTHENDELSE_RETRY_TOPIC)
    )
    createListenerOversikthendelseRetry(applicationState) {
        blockingApplicationLogicOversikthendelseRetry(
            applicationState,
            kafkaConsumerOversikthendelseRetry,
            oversikthendelseRetryService,
            toggleRetry
        )
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, KOppfolgingsplanLPSNAV>,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService,
    toggleProcessing: Boolean
) {
    while (applicationState.running) {
        var logValues = arrayOf(
            StructuredArguments.keyValue("id", "missing"),
            StructuredArguments.keyValue("timestamp", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            val callId = kafkaCallId()
            val kOppfolgingsplanLPSNAV: KOppfolgingsplanLPSNAV = it.value()
            logValues = arrayOf(
                StructuredArguments.keyValue("id", it.key()),
                StructuredArguments.keyValue("timestamp", it.timestamp())
            )
            LOG.info("Received KOppfolgingsplanLPSNAV, ready to process, $logKeys, {}", *logValues, callIdArgument(callId))

            if (toggleProcessing) {
                oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(
                    kOppfolgingsplanLPSNAV,
                    callId
                )
            }
        }
        delay(100)
    }
}

fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    launch {
        try {
            action()
        } finally {
            applicationState.running = false
        }
    }
