package no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka

import io.ktor.util.*
import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.*
import no.nav.syfo.kafka.kafkaConsumerConfig
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.kafkaCallId
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

private val LOG: Logger = LoggerFactory.getLogger("no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka")

const val OPPFOLGINGSPLAN_LPS_NAV_TOPIC = "aapen-syfo-oppfolgingsplan-lps-nav-v1"

@KtorExperimentalAPI
suspend fun blockingApplicationLogicOppfolgingsplanLPS(
    applicationState: ApplicationState,
    environment: Environment,
    vaultSecrets: VaultSecrets,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService
) {
    LOG.info("Setting up kafka consumer OppfolgingsplanLPS")

    val consumerProperties = kafkaConsumerConfig(environment, vaultSecrets)
    val kafkaConsumerOppfolgingsplanLPSNAV = KafkaConsumer<String, KOppfolgingsplanLPSNAV>(consumerProperties)

    kafkaConsumerOppfolgingsplanLPSNAV.subscribe(
        listOf(OPPFOLGINGSPLAN_LPS_NAV_TOPIC)
    )

    while (applicationState.running) {
        pollAndProcessKOppfolgingsplanLPSNAV(
            kafkaConsumerOppfolgingsplanLPSNAV = kafkaConsumerOppfolgingsplanLPSNAV,
            oppfolgingsplanLPSService = oppfolgingsplanLPSService
        )
        delay(100)
    }
}

fun pollAndProcessKOppfolgingsplanLPSNAV(
    kafkaConsumerOppfolgingsplanLPSNAV: KafkaConsumer<String, KOppfolgingsplanLPSNAV>,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService
) {
    var logValues = arrayOf(
        StructuredArguments.keyValue("id", "missing"),
        StructuredArguments.keyValue("timestamp", "missing")
    )

    val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
        "{}"
    }

    kafkaConsumerOppfolgingsplanLPSNAV.poll(Duration.ofMillis(0)).forEach {
        val callId = kafkaCallId()
        val kOppfolgingsplanLPSNAV: KOppfolgingsplanLPSNAV = it.value()
        logValues = arrayOf(
            StructuredArguments.keyValue("id", it.key()),
            StructuredArguments.keyValue("timestamp", it.timestamp())
        )
        LOG.info("Received KOppfolgingsplanLPSNAV, ready to process, $logKeys, {}", *logValues, callIdArgument(callId))

        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(
            kOppfolgingsplanLPSNAV,
            callId
        )
    }
}

fun CoroutineScope.createListenerOppfolgingsplanLPS(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job =
    launch {
        try {
            action()
        } finally {
            applicationState.running = false
        }
    }
