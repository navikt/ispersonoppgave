package no.nav.syfo.behandlerdialog.kafka

import no.nav.syfo.*
import no.nav.syfo.behandlerdialog.UbesvartMeldingService
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.domain.toMelding
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding.Companion.UBESVART_MELDING_TOPIC
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.kafka.*
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_UBESVART_MELDING_MOTTATT
import org.apache.kafka.clients.consumer.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.*
import java.util.*

fun launchKafkaTaskUbesvartMelding(
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
    launchKafkaTask(
        applicationState = applicationState,
        kafkaConsumerService = kafkaUbesvartMelding,
        consumerProperties = consumerProperties,
        topic = UBESVART_MELDING_TOPIC,
    )
}

class KafkaUbesvartMelding(
    private val database: DatabaseInterface,
    private val ubesvartMeldingService: UbesvartMeldingService,
) : KafkaConsumerService<KMeldingDTO> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KMeldingDTO>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            log.info("UbesvartMelding trace: Received ${records.count()} records")
            processRecords(
                database,
                records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(
        database: DatabaseInterface,
        records: ConsumerRecords<String, KMeldingDTO>,
    ) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecords.size
            log.warn("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
        }

        database.connection.use { connection ->
            validRecords.forEach { record ->
                val kMelding = record.value()
                val kafkaKey = UUID.fromString(record.key())
                log.info("Received ubesvart melding with key: $kafkaKey of melding with uuid ${kMelding.uuid}")

                ubesvartMeldingService.processUbesvartMelding(
                    melding = kMelding.toMelding(),
                    connection = connection,
                )
                COUNT_PERSONOPPGAVEHENDELSE_UBESVART_MELDING_MOTTATT.increment()
            }
            connection.commit()
        }
    }

    companion object {
        const val UBESVART_MELDING_TOPIC = "teamsykefravr.ubesvart-melding"
        val log: Logger = LoggerFactory.getLogger(KafkaUbesvartMelding::class.java)
    }
}
