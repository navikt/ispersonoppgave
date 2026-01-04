package no.nav.syfo.infrastructure.kafka.sykmelding

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.infrastructure.database.SykmeldingFieldsRepository
import no.nav.syfo.infrastructure.kafka.KafkaConsumerService
import no.nav.syfo.infrastructure.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.infrastructure.kafka.launchKafkaTask
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.PersonOppgave
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.infrastructure.database.queries.getPersonOppgaverByReferanseUuid
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.*
import java.util.*

const val SYKMELDING_TOPIC = "teamsykmelding.ok-sykmelding"
const val MANUELL_SYKMELDING_TOPIC = "teamsykmelding.manuell-behandling-sykmelding"

fun launchKafkaTaskSykmelding(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    personOppgaveRepository: PersonOppgaveRepository,
) {
    val consumerProperties = kafkaAivenConsumerConfig<ReceivedSykmeldingDTODeserializer>(environment.kafka).apply {
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
    }
    launchKafkaTask(
        applicationState = applicationState,
        kafkaConsumerService = SykmeldingConsumer(
            database = database,
            personOppgaveRepository = personOppgaveRepository,
        ),
        consumerProperties = consumerProperties,
        topics = listOf(SYKMELDING_TOPIC, MANUELL_SYKMELDING_TOPIC),
    )
}

class SykmeldingConsumer(
    private val database: DatabaseInterface,
    private val personOppgaveRepository: PersonOppgaveRepository,
) : KafkaConsumerService<ReceivedSykmeldingDTO> {

    override val pollDurationInMillis: Long = 1000
    private val sykmeldingFieldsRepository = SykmeldingFieldsRepository()

    override fun pollAndProcessRecords(
        kafkaConsumer: KafkaConsumer<String, ReceivedSykmeldingDTO>,
    ) {
        val records = kafkaConsumer.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processSykmeldingRecords(
                database = database,
                consumerRecords = records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processSykmeldingRecords(
        database: DatabaseInterface,
        consumerRecords: ConsumerRecords<String, ReceivedSykmeldingDTO>,
    ) {
        database.connection.use { connection ->
            consumerRecords.forEach { sykmeldingRecord ->
                sykmeldingRecord.value()?.let { receivedSykmeldingDTO ->
                    processSykmelding(receivedSykmeldingDTO, connection)
                }
            }
            connection.commit()
        }
    }

    private fun processSykmelding(
        receivedSykmeldingDTO: ReceivedSykmeldingDTO,
        connection: Connection,
    ) {
        COUNT_MOTTATT_SYKMELDING.increment()
        val sykmelding = receivedSykmeldingDTO.sykmelding
        val relevantFields = listOfNotNull(
            sykmelding.meldingTilNAV?.beskrivBistand,
            sykmelding.tiltakNAV,
            sykmelding.andreTiltak,
        ).filter { it.isNotEmpty() }

        if (relevantFields.isNotEmpty()) {
            if (relevantFields.all { hasIrrelevantContent(it) }) {
                COUNT_MOTTATT_SYKMELDING_SKIPPED_IRRELEVANT_TEXT.increment()
            } else {
                if (relevantFields.all { it.length < 10 }) {
                    COUNT_MOTTATT_SYKMELDING_SHORT_TEXT.increment()
                }
                createPersonoppgave(
                    connection = connection,
                    receivedSykmeldingDTO = receivedSykmeldingDTO,
                )
            }
        }
    }

    private fun hasIrrelevantContent(content: String): Boolean =
        irrelevantSykmeldingFelterContent.contains(content.lowercase().trim())

    private fun createPersonoppgave(
        connection: Connection,
        receivedSykmeldingDTO: ReceivedSykmeldingDTO,
    ) {
        val referanseUuid = UUID.fromString(receivedSykmeldingDTO.sykmelding.id)
        val hasExistingUbehandlet = connection.getPersonOppgaverByReferanseUuid(referanseUuid)
            .any { it.behandletTidspunkt == null }
        if (!hasExistingUbehandlet) {
            val arbeidstakerPersonident = PersonIdent(receivedSykmeldingDTO.personNrPasient)
            val existingDuplicate = sykmeldingFieldsRepository.findExistingPersonoppgaveFromSykmeldingFields(
                personident = arbeidstakerPersonident,
                tiltakNav = receivedSykmeldingDTO.sykmelding.tiltakNAV,
                tiltakAndre = receivedSykmeldingDTO.sykmelding.andreTiltak,
                bistand = receivedSykmeldingDTO.sykmelding.meldingTilNAV?.beskrivBistand,
                connection = connection,
            ).firstOrNull()
            val hasExistingDuplicate = existingDuplicate != null

            if (hasExistingDuplicate) {
                log.info("Received sykmelding with duplicate fields: ${existingDuplicate.second}")
                sykmeldingFieldsRepository.incrementDuplicateCount(
                    personoppgaveId = existingDuplicate.first,
                    connection = connection,
                )
                COUNT_MOTTATT_SYKMELDING_DUPLICATE.increment()
            } else {
                val personOppgaveId = personOppgaveRepository.createPersonoppgave(
                    personOppgave = PersonOppgave(
                        referanseUuid = referanseUuid,
                        personIdent = arbeidstakerPersonident,
                        type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND,
                        publish = true,
                        duplikatReferanseUuid = null,
                    ),
                    connection = connection
                )
                sykmeldingFieldsRepository.createPersonoppgaveSykmeldingFields(
                    personoppgaveId = personOppgaveId,
                    tiltakNav = receivedSykmeldingDTO.sykmelding.tiltakNAV,
                    tiltakAndre = receivedSykmeldingDTO.sykmelding.andreTiltak,
                    bistand = receivedSykmeldingDTO.sykmelding.meldingTilNAV?.beskrivBistand,
                    connection = connection,
                )
                COUNT_MOTTATT_SYKMELDING_SUCCESS.increment()
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SykmeldingConsumer::class.java)
        val irrelevantSykmeldingFelterContent = listOf(".", "-", "nei")
    }
}

class ReceivedSykmeldingDTODeserializer : Deserializer<ReceivedSykmeldingDTO> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): ReceivedSykmeldingDTO =
        mapper.readValue(data, ReceivedSykmeldingDTO::class.java)
}
