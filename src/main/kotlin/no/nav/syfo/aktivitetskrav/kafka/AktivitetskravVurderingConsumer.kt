package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.aktivitetskrav.VurderStansService
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingConsumer.Companion.AKTIVITETSKRAV_VURDERING_TOPIC
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.kafka.KafkaConsumerService
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.kafka.launchKafkaTask
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

fun launchKafkaTaskAktivitetskravVurdering(
    applicationState: ApplicationState,
    environment: Environment,
    vurderStansService: VurderStansService,
) {
    val consumerProperties = kafkaAivenConsumerConfig<AktivitetskravVurderingDeserializer>(environment.kafka)
    consumerProperties.apply {
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
    }
    val aktivitetskravVurderingConsumer = AktivitetskravExpiredVarselConsumer(
        vurderStansService = vurderStansService,
    )
    launchKafkaTask(
        applicationState = applicationState,
        kafkaConsumerService = aktivitetskravVurderingConsumer,
        consumerProperties = consumerProperties,
        topic = AKTIVITETSKRAV_VURDERING_TOPIC,
    )
}

class AktivitetskravVurderingConsumer(
    private val vurderStansService: VurderStansService,
) : KafkaConsumerService<KafkaAktivitetskravVurdering> {

    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaAktivitetskravVurdering>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            log.info("KafkaAktivitetskravVurderingConsumer trace: Received ${records.count()} records")
            processRecords(records = records)
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, KafkaAktivitetskravVurdering>) {
        val validRecords = records.requireNoNulls()
        vurderStansService.processAktivitetskravVurdering(
            aktivitetskravVurderinger = validRecords
                .map { it.value() }
                .filter { it.sistVurdert != null && it.updatedBy != null }
                .map { it.toAktivitetskravVurdering() },
        )
    }

    companion object {
        const val AKTIVITETSKRAV_VURDERING_TOPIC = "teamsykefravr.aktivitetskrav-vurdering"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
