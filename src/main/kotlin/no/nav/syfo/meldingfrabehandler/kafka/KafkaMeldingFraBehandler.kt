package no.nav.syfo.meldingfrabehandler.kafka

import no.nav.syfo.*
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.meldingfrabehandler.domain.KMeldingFraBehandler
import no.nav.syfo.meldingfrabehandler.domain.toMeldingFraBehandler
import no.nav.syfo.meldingfrabehandler.processMeldingFraBehandler
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTAT
import org.apache.kafka.clients.consumer.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.*
import java.util.*

const val MELDING_FRA_BEHANDLER_TOPIC = "teamsykefravr.melding-fra-behandler"
val log: Logger = LoggerFactory.getLogger("no.nav.syfo.meldingfrabehandler.kafka.KafkaMeldingFraBehandler")

const val pollDurationInMillis: Long = 1000

fun consumeMeldingFraBehandler(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment
) {
    val kafkaConfig = kafkaMeldingFraBehandlerConfig(environment.kafka)
    val kafkaConsumer = KafkaConsumer<String, KMeldingFraBehandler>(kafkaConfig)
    kafkaConsumer.subscribe(
        listOf(MELDING_FRA_BEHANDLER_TOPIC)
    )

    while (applicationState.ready) {
        pollAndProcessMeldingFraBehandler(
            database = database,
            kafkaConsumer = kafkaConsumer,
        )
    }
}

fun pollAndProcessMeldingFraBehandler(
    database: DatabaseInterface,
    kafkaConsumer: KafkaConsumer<String, KMeldingFraBehandler>,
) {
    val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
    if (records.count() > 0) {
        log.info("MeldingFraBehandler trace: Received ${records.count()} records")
        processRecords(
            database,
            records,
        )
        kafkaConsumer.commitSync()
    }
}

fun processRecords(
    database: DatabaseInterface,
    records: ConsumerRecords<String, KMeldingFraBehandler>,
) {
    val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

    if (tombstoneRecords.isNotEmpty()) {
        val numberOfTombstones = tombstoneRecords.size
        log.warn("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
    }

    database.connection.use { connection ->
        validRecords.forEach { record ->
            val kMeldingFraBehandler = record.value()
            val kafkaKey = UUID.fromString(record.key())
            log.info("Received meldingFraBehandler with key: $kafkaKey of melding with uuid ${kMeldingFraBehandler.uuid}")

            processMeldingFraBehandler(
                meldingFraBehandler = kMeldingFraBehandler.toMeldingFraBehandler(),
                connection = connection,
            )
            COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTAT.increment()
        }
        connection.commit()
    }
}
