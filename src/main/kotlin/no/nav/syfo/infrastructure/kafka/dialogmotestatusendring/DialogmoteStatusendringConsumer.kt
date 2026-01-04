package no.nav.syfo.infrastructure.kafka.dialogmotestatusendring

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.EnvironmentKafka
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.domain.DialogmoteStatusendring
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.domain.didFinishDialogmote
import no.nav.syfo.domain.happenedAfter
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.queries.*
import no.nav.syfo.infrastructure.kafka.KafkaConsumerService
import no.nav.syfo.infrastructure.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.infrastructure.kafka.launchKafkaTask
import no.nav.syfo.util.toLocalDateTimeOslo
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.*

fun launchKafkaTaskDialogmotestatusendring(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment,
) {
    val consumerProperties = kafkaConfig(environment.kafka)
    val dialogmoteStatusendringConsumer = DialogmoteStatusendringConsumer(database = database)
    launchKafkaTask(
        applicationState = applicationState,
        consumerProperties = consumerProperties,
        topics = listOf(DialogmoteStatusendringConsumer.DIALOGMOTE_STATUSENDRING_TOPIC),
        kafkaConsumerService = dialogmoteStatusendringConsumer,
    )
}

private fun kafkaConfig(environmentKafka: EnvironmentKafka): Properties {
    return Properties().apply {
        putAll(kafkaAivenConsumerConfig<KafkaAvroDeserializer>(environmentKafka))
        this[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = environmentKafka.aivenSchemaRegistryUrl
        this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
        this[KafkaAvroDeserializerConfig.USER_INFO_CONFIG] =
            "${environmentKafka.aivenRegistryUser}:${environmentKafka.aivenRegistryPassword}"
        this[KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
    }
}

class DialogmoteStatusendringConsumer(private val database: DatabaseInterface) :
    KafkaConsumerService<KDialogmoteStatusEndring> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KDialogmoteStatusEndring>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(
                database,
                records,
            )

            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(
        database: DatabaseInterface,
        records: ConsumerRecords<String, KDialogmoteStatusEndring>,
    ) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }

        if (tombstoneRecords.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecords.size
            log.warn("Value of $numberOfTombstones ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
            COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_TOMBSTONE.increment(numberOfTombstones.toDouble())
        }

        database.connection.use { connection ->
            validRecords.forEach { record ->
                COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_READ.increment()
                log.info(
                    "Received statusendring with key : ${record.key()} of type " +
                        "${record.value().getStatusEndringType()} for dialogmoteUuid ${record.value().getDialogmoteUuid()}"
                )

                val statusendring = DialogmoteStatusendring.create(record.value())
                storeDialogmoteStatusendring(
                    connection = connection,
                    statusendring = statusendring,
                )
                processDialogmoteStatusendring(
                    connection = connection,
                    statusendring = statusendring,
                )
            }
            connection.commit()
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(DialogmoteStatusendringConsumer::class.java)
        const val DIALOGMOTE_STATUSENDRING_TOPIC = "teamsykefravr.isdialogmote-dialogmote-statusendring"

        fun processDialogmoteStatusendring(
            connection: Connection,
            statusendring: DialogmoteStatusendring,
        ) {
            log.info("Received statusendring of type ${statusendring.type} and uuid ${statusendring.dialogmoteUuid}")

            val personOppgave = connection
                .getPersonOppgaverByReferanseUuid(statusendring.dialogmoteUuid)
                .map { it.toPersonOppgave() }
                .firstOrNull { it.type == PersonOppgaveType.DIALOGMOTESVAR }

            if (personOppgave == null) {
                val personoppgaveUuid = UUID.randomUUID()
                connection.createBehandletPersonoppgave(statusendring, personoppgaveUuid)
            } else {
                if (statusendring happenedAfter personOppgave || statusendring.didFinishDialogmote()) {
                    val updatedOppgave = personOppgave.copy(
                        behandletTidspunkt = statusendring.endringTidspunkt.toLocalDateTimeOslo(),
                        behandletVeilederIdent = statusendring.veilederIdent,
                        sistEndret = statusendring.endringTidspunkt.toLocalDateTimeOslo(),
                        publish = true,
                    )
                    connection.updatePersonoppgaveSetBehandlet(updatedOppgave)
                }
            }
        }

        fun storeDialogmoteStatusendring(
            connection: Connection,
            statusendring: DialogmoteStatusendring,
        ) {
            connection.createDialogmoteStatusendring(statusendring)
        }
    }
}
