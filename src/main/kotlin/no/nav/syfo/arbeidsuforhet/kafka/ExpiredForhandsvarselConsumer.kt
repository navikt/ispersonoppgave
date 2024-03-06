package no.nav.syfo.arbeidsuforhet.kafka

import no.nav.syfo.ApplicationState
import no.nav.syfo.EnvironmentKafka
import no.nav.syfo.arbeidsuforhet.VurderAvslagService
import no.nav.syfo.kafka.KafkaConsumerService
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.kafka.launchKafkaTask
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class ExpiredForhandsvarselConsumer(
    private val kafkaEnvironment: EnvironmentKafka,
    private val applicationState: ApplicationState,
    private val vurderAvslagService: VurderAvslagService,
) : KafkaConsumerService<ExpiredForhandsvarsel> {
    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, ExpiredForhandsvarsel>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            log.info("ExpiredForhandsvarselConsumer trace: Received ${records.count()} records")
            processRecords(records)
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, ExpiredForhandsvarsel>) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecords.size
            log.warn("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
        }

        vurderAvslagService.processExpiredForhandsvarsel(expiredForhandsvarselList = validRecords.map { it.value() })
    }

    fun launch() {
        val consumerProperties = kafkaAivenConsumerConfig<ExpiredForhandsvarselDeserializer>(environmentKafka = kafkaEnvironment)
        launchKafkaTask(
            applicationState = applicationState,
            kafkaConsumerService = this,
            consumerProperties = consumerProperties,
            topics = listOf(ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_TOPIC),
        )
    }

    companion object {
        const val ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_TOPIC = "teamsykefravr.arbeidsuforhet-expired-forhandsvarsel"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
