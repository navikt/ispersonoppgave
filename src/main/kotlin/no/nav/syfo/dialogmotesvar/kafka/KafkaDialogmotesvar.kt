package no.nav.syfo.dialogmotesvar.kafka

import no.nav.syfo.*
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.dialogmotesvar.domain.KDialogmotesvar
import no.nav.syfo.dialogmotesvar.domain.toDialogmotesvar
import no.nav.syfo.dialogmotesvar.processDialogmotesvar
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

const val DIALOGMOTESVAR_TOPIC = "teamsykefravr.dialogmotesvar"

val pollDurationInMillis: Long = 1000

fun consumeDialogmotesvar(
    database: DatabaseInterface,
    applicationState: ApplicationState,
    environment: Environment
) {
    val kafkaConfig = kafkaConfig(environment.kafka)
    val kafkaConsumer = KafkaConsumer<String, KDialogmotesvar>(kafkaConfig)
    kafkaConsumer.subscribe(
        listOf(DIALOGMOTESVAR_TOPIC)
    )

    while (applicationState.ready) {
        pollAndProcessDialogmotesvar(
            database = database,
            kafkaConsumer = kafkaConsumer,
        )
    }
}

private fun kafkaConfig(environmentKafka: EnvironmentKafka): Properties {
    return Properties().apply {
        putAll(kafkaAivenConsumerConfig(environmentKafka))
        this[ConsumerConfig.GROUP_ID_CONFIG] = "ispersonoppgave-v1"
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KDialogmotesvarDeserializer::class.java.canonicalName
    }
}

class KDialogmotesvarDeserializer : Deserializer<KDialogmotesvar> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KDialogmotesvar =
        mapper.readValue(data, KDialogmotesvar::class.java)
}

fun pollAndProcessDialogmotesvar(
    database: DatabaseInterface,
    kafkaConsumer: KafkaConsumer<String, KDialogmotesvar>
) {
    val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
    if (records.count() > 0) {
        log.info("TRACE: Received ${records.count()} records")
        processRecords(
            database,
            records,
        )

        kafkaConsumer.commitSync()
    }
}

fun processRecords(
    database: DatabaseInterface,
    records: ConsumerRecords<String, KDialogmotesvar>
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
            processDialogmotesvar(
                connection = connection,
                dialogmotesvar = dialogmotesvar,
            )
        }
        connection.commit()
    }
}

val log: org.slf4j.Logger = LoggerFactory.getLogger("no.nav.syfo.dialogmotesvar.kafka.KafkaDialogmotesvar")
