package no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka

import io.ktor.util.*
import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.ApplicationState
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
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

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListenerOppfolgingsplanLPS(
    applicationState: ApplicationState,
    consumerProperties: Properties,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService
) {
    val kafkaConsumerOppfolgingsplanLPSNAV = KafkaConsumer<String, KOppfolgingsplanLPSNAV>(consumerProperties)

    kafkaConsumerOppfolgingsplanLPSNAV.subscribe(
        listOf(OPPFOLGINGSPLAN_LPS_NAV_TOPIC)
    )

    createListenerOppfolgingsplanLPS(applicationState) {
        blockingApplicationLogicOppfolgingsplanLPS(
            applicationState,
            kafkaConsumerOppfolgingsplanLPSNAV,
            oppfolgingsplanLPSService
        )
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogicOppfolgingsplanLPS(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, KOppfolgingsplanLPSNAV>,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService
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

            oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(
                kOppfolgingsplanLPSNAV,
                callId
            )
        }
        delay(100)
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
