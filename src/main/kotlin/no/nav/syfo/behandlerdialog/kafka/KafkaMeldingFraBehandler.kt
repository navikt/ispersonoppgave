package no.nav.syfo.behandlerdialog.kafka

import no.nav.syfo.*
import no.nav.syfo.behandlerdialog.MeldingFraBehandlerService
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.kafka.*
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.domain.toMelding
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler.Companion.MELDING_FRA_BEHANDLER_TOPIC
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT
import org.apache.kafka.clients.consumer.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.*
import java.util.*

fun launchKafkaTaskMeldingFraBehandler(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment,
    meldingFraBehandlerService: MeldingFraBehandlerService,
) {
    val kafkaMeldingFraBehandler = KafkaMeldingFraBehandler(
        database = database,
        meldingFraBehandlerService = meldingFraBehandlerService,
    )
    val consumerProperties = kafkaAivenConsumerConfig<KMeldingDTODeserializer>(environment.kafka)
    consumerProperties.apply {
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
    }
    launchKafkaTask(
        applicationState = applicationState,
        kafkaConsumerService = kafkaMeldingFraBehandler,
        consumerProperties = consumerProperties,
        topic = MELDING_FRA_BEHANDLER_TOPIC,
    )
}

class KafkaMeldingFraBehandler(
    private val database: DatabaseInterface,
    private val meldingFraBehandlerService: MeldingFraBehandlerService,
) : KafkaConsumerService<KMeldingDTO> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KMeldingDTO>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            log.info("MeldingFraBehandler trace: Received ${records.count()} records")
            processRecords(
                database = database,
                records = records,
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
                log.info("Received meldingFraBehandler with key=$kafkaKey, uuid=${kMelding.uuid} and parentRef=${kMelding.parentRef}")

                meldingFraBehandlerService.processMeldingFraBehandler(
                    melding = kMelding.toMelding(),
                    connection = connection,
                )
                COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT.increment()
            }
            connection.commit()
        }

        validRecords.forEach { record ->
            val melding = record.value().toMelding()
            meldingFraBehandlerService.handleExistingUbesvartMeldingOppgave(melding)
        }
    }

    companion object {
        const val MELDING_FRA_BEHANDLER_TOPIC = "teamsykefravr.melding-fra-behandler"
        val log: Logger = LoggerFactory.getLogger(KafkaMeldingFraBehandler::class.java)
    }
}
