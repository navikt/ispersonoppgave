package no.nav.syfo.oppfolgingsplanlps.kafka

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.*
import no.nav.syfo.kafka.*
import no.nav.syfo.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.util.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

private val LOG: Logger = LoggerFactory.getLogger("no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka")

const val OPPFOLGINGSPLAN_LPS_NAV_TOPIC = "team-esyfo.aapen-syfo-oppfolgingsplan-lps-nav-v2"

fun launchKafkaTaskOppfolgingsplanLPS(
    applicationState: ApplicationState,
    environment: Environment,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService,
) {
    val kafkaOppfolgingsplanLPS = KafkaOppfolgingsplanLPS(oppfolgingsplanLPSService = oppfolgingsplanLPSService)
    val consumerProperties = kafkaAivenConsumerConfig<KOppfolgingsplanLPSDeserializer>(environment.kafka)
    launchKafkaTask(
        applicationState = applicationState,
        kafkaConsumerService = kafkaOppfolgingsplanLPS,
        consumerProperties = consumerProperties,
        topics = listOf(OPPFOLGINGSPLAN_LPS_NAV_TOPIC),
    )
}

class KafkaOppfolgingsplanLPS(private val oppfolgingsplanLPSService: OppfolgingsplanLPSService) :
    KafkaConsumerService<KOppfolgingsplanLPS> {
    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KOppfolgingsplanLPS>) {
        var logValues = arrayOf(
            StructuredArguments.keyValue("id", "missing"),
            StructuredArguments.keyValue("timestamp", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }

        val records = kafkaConsumer.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            records.forEach {
                val callId = kafkaCallId()
                val kOppfolgingsplanLPS: KOppfolgingsplanLPS = it.value()
                logValues = arrayOf(
                    StructuredArguments.keyValue("id", it.key()),
                    StructuredArguments.keyValue("timestamp", it.timestamp())
                )
                LOG.info(
                    "Received KOppfolgingsplanLPS, ready to process, $logKeys, {}",
                    *logValues,
                    callIdArgument(callId)
                )

                oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(
                    kOppfolgingsplanLPS,
                    callId
                )
            }
            kafkaConsumer.commitSync()
        }
    }
}

class KOppfolgingsplanLPSDeserializer : Deserializer<KOppfolgingsplanLPS> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KOppfolgingsplanLPS =
        mapper.readValue(data, KOppfolgingsplanLPS::class.java)
}
