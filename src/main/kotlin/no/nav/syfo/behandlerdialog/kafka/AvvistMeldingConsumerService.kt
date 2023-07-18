package no.nav.syfo.behandlerdialog.kafka

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.behandlerdialog.AvvistMeldingService
import no.nav.syfo.behandlerdialog.UbesvartMeldingService
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.domain.toMelding
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.kafka.KafkaConsumerService
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.kafka.launchKafkaTask
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

fun launchKafkaTaskAvvistMelding(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment,
    ubesvartMeldingService: UbesvartMeldingService,
) {
    val kafkaUbesvartMelding = KafkaUbesvartMelding(
        database = database,
        ubesvartMeldingService = ubesvartMeldingService,
    )
    val consumerProperties = kafkaAivenConsumerConfig<KMeldingDTODeserializer>(environment.kafka)
    consumerProperties.apply {
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
    }
    launchKafkaTask(
        applicationState = applicationState,
        kafkaConsumerService = kafkaUbesvartMelding,
        consumerProperties = consumerProperties,
        topic = KafkaUbesvartMelding.UBESVART_MELDING_TOPIC,
    )
}

class AvvistMeldingConsumerService (
    private val database: DatabaseInterface,
    private val avvistMeldingService: AvvistMeldingService,
) : KafkaConsumerService<KMeldingDTO> {

    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KMeldingDTO>) {
        // Poll records with consumer
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            log.info("AvvistMelding trace: Received ${records.count()} records")
            processRecords(records = records)
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, KMeldingDTO>) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecords.size
            log.warn("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
        }

        database.connection.use { connection ->
            validRecords.forEach { record ->
                val kMelding = record.value()
                val kafkaKey = UUID.fromString(record.key())
                log.info("Received avvistMelding with key=$kafkaKey, uuid=${kMelding.uuid} and parentRef=${kMelding.parentRef}")

                avvistMeldingService.processAvvistMelding(
                    melding = kMelding.toMelding(),
                    connection = connection,
                )
                // TODO: fix
                COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT.increment()
            }
            connection.commit()
        }
    }

    companion object {
        const val AVVIST_MELDING_TOPIC = "teamsykefravr.avvist-melding"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
