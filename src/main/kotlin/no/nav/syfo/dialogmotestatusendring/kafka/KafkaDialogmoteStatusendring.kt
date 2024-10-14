package no.nav.syfo.dialogmotestatusendring.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.syfo.*
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.processDialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.storeDialogmoteStatusendring
import no.nav.syfo.kafka.*
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

const val DIALOGMOTE_STATUSENDRING_TOPIC = "teamsykefravr.isdialogmote-dialogmote-statusendring"

fun launchKafkaTaskDialogmotestatusendring(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment,
) {
    val consumerProperties = kafkaConfig(environment.kafka)
    val kafkaDialogmoteStatusendring = KafkaDialogmoteStatusendring(database = database)
    launchKafkaTask(
        applicationState = applicationState,
        consumerProperties = consumerProperties,
        topics = listOf(DIALOGMOTE_STATUSENDRING_TOPIC),
        kafkaConsumerService = kafkaDialogmoteStatusendring,
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

class KafkaDialogmoteStatusendring(private val database: DatabaseInterface) :
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
                    "Received statusendring with key : ${record.key()} of type ${
                    record.value().getStatusEndringType()
                    }"
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
}

val log: org.slf4j.Logger =
    LoggerFactory.getLogger("no.nav.syfo.dialogmotestatusendring.kafka.KafkaDialogmoteStatusendringKt")
