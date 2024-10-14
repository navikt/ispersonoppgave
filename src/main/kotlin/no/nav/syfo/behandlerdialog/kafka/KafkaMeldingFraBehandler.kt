package no.nav.syfo.behandlerdialog.kafka

import no.nav.syfo.*
import no.nav.syfo.behandlerdialog.MeldingFraBehandlerService
import no.nav.syfo.kafka.*
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.domain.toMelding
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler.Companion.MELDING_FRA_BEHANDLER_TOPIC
import org.apache.kafka.clients.consumer.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.*

fun launchKafkaTaskMeldingFraBehandler(
    applicationState: ApplicationState,
    environment: Environment,
    meldingFraBehandlerService: MeldingFraBehandlerService,
) {
    val kafkaMeldingFraBehandler = KafkaMeldingFraBehandler(
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
        topics = listOf(MELDING_FRA_BEHANDLER_TOPIC),
    )
}

class KafkaMeldingFraBehandler(
    private val meldingFraBehandlerService: MeldingFraBehandlerService,
) : KafkaConsumerService<KMeldingDTO> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KMeldingDTO>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(
                records = records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(
        records: ConsumerRecords<String, KMeldingDTO>,
    ) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecords.size
            log.warn("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
        }
        val recordPairs = validRecords.map { record ->
            Pair(record.key(), record.value().toMelding())
        }
        meldingFraBehandlerService.processMeldingerFraBehandler(recordPairs)
    }

    companion object {
        const val MELDING_FRA_BEHANDLER_TOPIC = "teamsykefravr.melding-fra-behandler"
        val log: Logger = LoggerFactory.getLogger(KafkaMeldingFraBehandler::class.java)
    }
}
