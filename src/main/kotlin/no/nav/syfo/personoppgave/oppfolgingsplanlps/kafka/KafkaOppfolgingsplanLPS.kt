package no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka

import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.*
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.util.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

private val LOG: Logger = LoggerFactory.getLogger("no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka")

const val OPPFOLGINGSPLAN_LPS_NAV_TOPIC = "team-esyfo.aapen-syfo-oppfolgingsplan-lps-nav-v2"

suspend fun blockingApplicationLogicOppfolgingsplanLPS(
    applicationState: ApplicationState,
    environment: Environment,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService
) {
    LOG.info("Setting up kafka consumer OppfolgingsplanLPS")

    val consumerProperties = kafkaConfig(environment.kafka)
    val kafkaConsumerOppfolgingsplanLPS = KafkaConsumer<String, KOppfolgingsplanLPSNAV>(consumerProperties)

    kafkaConsumerOppfolgingsplanLPS.subscribe(
        listOf(OPPFOLGINGSPLAN_LPS_NAV_TOPIC)
    )

    while (applicationState.ready) {
        pollAndProcessKOppfolgingsplanLPS(
            kafkaConsumerOppfolgingsplanLPSNAV = kafkaConsumerOppfolgingsplanLPS,
            oppfolgingsplanLPSService = oppfolgingsplanLPSService
        )
        delay(100)
    }
}

fun pollAndProcessKOppfolgingsplanLPS(
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

private fun kafkaConfig(environmentKafka: EnvironmentKafka): Properties {
    return Properties().apply {
        putAll(kafkaAivenConsumerConfig(environmentKafka))
        this[ConsumerConfig.GROUP_ID_CONFIG] = "ispersonoppgave-v1"
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KOppfolgingsplanLPSDeserializer::class.java.canonicalName
    }
}

class KOppfolgingsplanLPSDeserializer : Deserializer<KOppfolgingsplanLPSNAV> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KOppfolgingsplanLPSNAV =
        mapper.readValue(data, KOppfolgingsplanLPSNAV::class.java)
}
