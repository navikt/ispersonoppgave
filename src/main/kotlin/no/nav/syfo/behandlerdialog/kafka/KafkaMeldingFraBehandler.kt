package no.nav.syfo.behandlerdialog.kafka

import no.nav.syfo.*
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.kafka.*
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.behandlerdialog.domain.toMelding
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler.Companion.MELDING_FRA_BEHANDLER_TOPIC
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.personoppgave.updatePersonoppgave
import no.nav.syfo.util.Constants
import no.nav.syfo.util.toLocalDateTimeOslo
import org.apache.kafka.clients.consumer.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.*
import java.util.*

fun launchKafkaTaskMeldingFraBehandler(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment,
) {
    val kafkaMeldingFraBehandler = KafkaMeldingFraBehandler(database = database)
    val consumerProperties = kafkaAivenConsumerConfig<KMeldingDTODeserializer>(environment.kafka)
    launchKafkaTask(
        applicationState = applicationState,
        kafkaConsumerService = kafkaMeldingFraBehandler,
        consumerProperties = consumerProperties,
        topic = MELDING_FRA_BEHANDLER_TOPIC,
    )
}

class KafkaMeldingFraBehandler(private val database: DatabaseInterface) : KafkaConsumerService<KMeldingDTO> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KMeldingDTO>) {
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
                log.info("Received meldingFraBehandler with key: $kafkaKey of melding with uuid ${kMelding.uuid}")

                processMeldingFraBehandler(
                    melding = kMelding.toMelding(),
                    connection = connection,
                )
                COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT.increment()
            }
            connection.commit()
        }
    }

    private fun processMeldingFraBehandler(
        melding: Melding,
        connection: Connection,
    ) {
        log.info("Received melding fra behandler with uuid: ${melding.referanseUuid}")
        val existingOppgave = connection
            .getPersonOppgaveByReferanseUuid(melding.referanseUuid)
            ?.toPersonOppgave()
        if (existingOppgave != null && existingOppgave.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART) {
            log.info("Received svar on ubesvart melding for oppgave with uuid ${existingOppgave.uuid}, behandles automatically by system")
            markOppgaveAsBehandletBySystem(
                personOppgave = existingOppgave,
                connection = connection,
            )
        }
        connection.createPersonOppgave(
            melding = melding,
            personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_SVAR,
        )
    }

    private fun markOppgaveAsBehandletBySystem(personOppgave: PersonOppgave, connection: Connection) {
        val now = OffsetDateTime.now().toLocalDateTimeOslo()
        val updatedOppgave = personOppgave.copy(
            behandletTidspunkt = now,
            behandletVeilederIdent = Constants.SYSTEM_VEILEDER_IDENT,
            sistEndret = now,
            publish = true,
        )
        connection.updatePersonoppgave(updatedOppgave)
    }

    companion object {
        const val MELDING_FRA_BEHANDLER_TOPIC = "teamsykefravr.melding-fra-behandler"
        val log: Logger = LoggerFactory.getLogger(KafkaMeldingFraBehandler::class.java)
    }
}
