package no.nav.syfo.dialogmotesvar.kafka

import no.nav.syfo.*
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.dialogmotesvar.processDialogmotesvar
import no.nav.syfo.dialogmotesvar.storeDialogmotesvar
import no.nav.syfo.kafka.*
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.LoggerFactory
import java.time.*
import java.util.*

const val DIALOGMOTESVAR_TOPIC = "teamsykefravr.dialogmotesvar"

fun launchKafkaTaskDialogmotesvar(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment,
) {
    val consumerProperties = kafkaAivenConsumerConfig<KDialogmotesvarDeserializer>(environmentKafka = environment.kafka)
    launchKafkaTask(
        applicationState = applicationState,
        consumerProperties = consumerProperties,
        topics = listOf(DIALOGMOTESVAR_TOPIC),
        kafkaConsumerService = KafkaDialogmotesvarConsumer(
            database = database,
            cutoffDate = environment.outdatedDialogmotesvarCutoff,
        ),
    )
}

class KafkaDialogmotesvarConsumer(
    private val database: DatabaseInterface,
    private val cutoffDate: LocalDate,
) : KafkaConsumerService<KDialogmotesvar> {
    override val pollDurationInMillis: Long = 1000
    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KDialogmotesvar>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            log.info("Dialogmotesvar trace: Received ${records.count()} records")
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
}

class KDialogmotesvarDeserializer : Deserializer<KDialogmotesvar> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KDialogmotesvar =
        mapper.readValue(data, KDialogmotesvar::class.java)
}

val log: org.slf4j.Logger = LoggerFactory.getLogger("no.nav.syfo.dialogmotesvar.kafka.KafkaDialogmotesvar")
