package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.aktivitetskrav.domain.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.kafka.ForhandsvarselExpiredVarselConsumerService.Companion.AKTIVITETSKRAV_EXPIRED_VARSEL_TOPIC
import no.nav.syfo.kafka.KafkaConsumerService
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.kafka.launchKafkaTask
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

fun launchKafkaTaskForhandsvarselExpiredVarsel(
    applicationState: ApplicationState,
    environment: Environment,
) {
    val consumerProperties = kafkaAivenConsumerConfig<ExpiredVarselDeserializer>(environment.kafka).apply {
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
    }
    val forhandsvarselExpiredVarselConsumerService = ForhandsvarselExpiredVarselConsumerService()
    launchKafkaTask(
        applicationState = applicationState,
        kafkaConsumerService = forhandsvarselExpiredVarselConsumerService,
        consumerProperties = consumerProperties,
        topic = AKTIVITETSKRAV_EXPIRED_VARSEL_TOPIC,
    )
}

class ForhandsvarselExpiredVarselConsumerService() : KafkaConsumerService<ExpiredVarsel> {

    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, ExpiredVarsel>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            log.info("ForhandsvarselExpiredVarselConsumerService trace: Received ${records.count()} records")
            processRecords(records = records)
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, ExpiredVarsel>) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecords.size
            log.warn("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
        }

        val recordPairs = validRecords.map { record ->
            Pair(record.key(), record.value())
        }
        // TODO: process values
    }

    companion object {
        const val AKTIVITETSKRAV_EXPIRED_VARSEL_TOPIC = "teamsykefravr.aktivitetskrav-expired-varsel"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
