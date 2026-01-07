package no.nav.syfo.infrastructure.kafka.dialogmotesvar

import io.micrometer.core.instrument.Counter
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.METRICS_NS
import no.nav.syfo.METRICS_REGISTRY
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.queries.*
import no.nav.syfo.infrastructure.kafka.KafkaConsumerService
import no.nav.syfo.infrastructure.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.infrastructure.kafka.launchKafkaTask
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.toLocalDateTimeOslo
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.time.LocalDate
import java.util.*

fun launchKafkaTaskDialogmotesvar(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment,
) {
    val consumerProperties =
        kafkaAivenConsumerConfig<KDialogmotesvarDeserializer>(environmentKafka = environment.kafka)
    launchKafkaTask(
        applicationState = applicationState,
        consumerProperties = consumerProperties,
        topics = listOf(DialogmotesvarConsumer.DIALOGMOTESVAR_TOPIC),
        kafkaConsumerService = DialogmotesvarConsumer(
            database = database,
            cutoffDate = environment.outdatedDialogmotesvarCutoff,
        ),
    )
}

class DialogmotesvarConsumer(
    private val database: DatabaseInterface,
    private val cutoffDate: LocalDate,
) : KafkaConsumerService<KDialogmotesvar> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KDialogmotesvar>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(records)

            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(
        records: ConsumerRecords<String, KDialogmotesvar>,
    ) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecords.size
            log.warn("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
        }

        database.connection.use { connection ->
            validRecords.forEach { record ->
                val kDialogmotesvar = record.value()
                val moteUuid = UUID.fromString(record.key())
                log.info("Received dialogmotesvar with key/moteuuid : $moteUuid of svar type ${kDialogmotesvar.svarType}")

                val dialogmotesvar = kDialogmotesvar.toDialogmotesvar(moteUuid)
                storeDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                )
                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                    cutoffDate = cutoffDate,
                )
            }
            connection.commit()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DialogmotesvarConsumer::class.java)
        const val DIALOGMOTESVAR_TOPIC = "teamsykefravr.dialogmotesvar"

        fun processDialogmotesvar(
            connection: Connection,
            dialogmotesvar: Dialogmotesvar,
            cutoffDate: LocalDate,
        ) {
            log.info("Received dialogmotesvar! ${dialogmotesvar.moteuuid}")
            if (dialogmotesvar.isIrrelevant(cutoffDate) || isDialogmoteClosed(connection, dialogmotesvar)) return

            val personOppgave = connection
                .getPersonOppgaverByReferanseUuid(dialogmotesvar.moteuuid)
                .map { it.toPersonOppgave() }
                .firstOrNull { it.type == PersonOppgaveType.DIALOGMOTESVAR }

            if (personOppgave == null) {
                connection.createPersonOppgave(dialogmotesvar)
            } else {
                if (dialogmotesvar happenedAfter personOppgave) {
                    val updatedOppgave = personOppgave.copy(
                        behandletTidspunkt = null,
                        behandletVeilederIdent = null,
                        sistEndret = dialogmotesvar.svarReceivedAt.toLocalDateTimeOslo(),
                        publish = true,
                    )
                    connection.updatePersonoppgaveSetBehandlet(updatedOppgave)
                    Metrics.COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED.increment()
                    if (dialogmotesvar.svarType == DialogmoteSvartype.KOMMER) {
                        Metrics.COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER.increment()
                    }
                }
            }
        }

        fun storeDialogmotesvar(
            connection: Connection,
            dialogmotesvar: Dialogmotesvar,
        ) {
            connection.createDialogmotesvar(dialogmotesvar)
        }

        fun isDialogmoteClosed(connection: Connection, dialogmotesvar: Dialogmotesvar): Boolean {
            val dialogmoteStatusendring = connection.getDialogmoteStatusendring(dialogmotesvar.moteuuid)
            val latestStatusEndring = dialogmoteStatusendring.maxByOrNull { it.endringTidspunkt }

            return latestStatusEndring != null && latestStatusEndring.didFinishDialogmote()
        }
    }
}

class KDialogmotesvarDeserializer : Deserializer<KDialogmotesvar> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KDialogmotesvar =
        mapper.readValue(data, KDialogmotesvar::class.java)
}

private object Metrics {
    const val DIALOGMOTESVAR_OPPGAVE_UPDATED = "${METRICS_NS}_dialogmotesvar_oppgave_updated_count"
    val COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED: Counter =
        Counter.builder(DIALOGMOTESVAR_OPPGAVE_UPDATED)
            .description("Counts the number of PERSON_OPPGAVE updated from a KDialogmotesvar")
            .register(METRICS_REGISTRY)
    const val DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER = "${METRICS_NS}_dialogmotesvar_oppgave_updated_kommer_count"
    val COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER: Counter =
        Counter.builder(DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER)
            .description("Counts the number of PERSON_OPPGAVE updated from a KDialogmotesvar KOMMER")
            .register(METRICS_REGISTRY)
}
