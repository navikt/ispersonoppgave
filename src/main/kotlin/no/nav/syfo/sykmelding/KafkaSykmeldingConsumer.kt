package no.nav.syfo.behandler.kafka.sykmelding

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.domain.*
import no.nav.syfo.kafka.KafkaConsumerService
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.kafka.launchKafkaTask
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import no.nav.syfo.sykmelding.*
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
        kafkaConsumerService = KafkaSykmeldingConsumer(
            database = database,
            personOppgaveRepository = personOppgaveRepository
        ),
        consumerProperties = consumerProperties,
        topics = listOf(SYKMELDING_TOPIC, MANUELL_SYKMELDING_TOPIC),
    )
}

class KafkaSykmeldingConsumer(
    private val database: DatabaseInterface,
    private val personOppgaveRepository: PersonOppgaveRepository,
) : KafkaConsumerService<ReceivedSykmeldingDTO> {

    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(
        kafkaConsumer: KafkaConsumer<String, ReceivedSykmeldingDTO>,
    ) {
        val records = kafkaConsumer.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processSykmelding(
                database = database,
                consumerRecords = records,
            )
            kafkaConsumer.commitSync()
        }
    }

    private fun processSykmelding(
        database: DatabaseInterface,
        consumerRecords: ConsumerRecords<String, ReceivedSykmeldingDTO>,
    ) {
        database.connection.use { connection ->
            consumerRecords.forEach {
                it.value()?.let { receivedSykmeldingDTO ->
                    COUNT_MOTTATT_SYKMELDING.increment()
                    val sykmelding = receivedSykmeldingDTO.sykmelding
                    if (!sykmelding.meldingTilNAV?.beskrivBistand.isNullOrEmpty() ||
                        !sykmelding.tiltakNAV.isNullOrEmpty() ||
                        !sykmelding.andreTiltak.isNullOrEmpty()
                    ) {
                        createPersonoppgave(
                            connection = connection,
                            receivedSykmeldingDTO = receivedSykmeldingDTO,
                        )
                        countIrrelevantSykmeldingFelter(sykmelding)
                    }
                }
            }
            connection.commit()
        }
    }

    private fun countIrrelevantSykmeldingFelter(sykmelding: Sykmelding) {
        val beskrivBistandNav = sykmelding.meldingTilNAV?.beskrivBistand
        val tiltakNav = sykmelding.tiltakNAV
        val andreTiltak = sykmelding.andreTiltak
        if (!beskrivBistandNav.isNullOrEmpty() && isIrrelevant(beskrivBistandNav)) {
            COUNT_MOTTATT_SYKMELDING_BESKRIV_BISTAND_NAV_IRRELEVANT.increment()
        }
        if (!tiltakNav.isNullOrEmpty() && isIrrelevant(tiltakNav)) {
            COUNT_MOTTATT_SYKMELDING_TILTAK_NAV_IRRELEVANT.increment()
        }
        if (!andreTiltak.isNullOrEmpty() && isIrrelevant(andreTiltak)) {
            COUNT_MOTTATT_SYKMELDING_ANDRE_TILTAK_IRRELEVANT.increment()
        }
    }

    private fun isIrrelevant(content: String): Boolean =
        irrelevantSykmeldingFelterContent.contains(content.lowercase().trim())

    private fun createPersonoppgave(
        connection: Connection,
        receivedSykmeldingDTO: ReceivedSykmeldingDTO,
    ) {
        val referanseUuid = UUID.fromString(receivedSykmeldingDTO.sykmelding.id)
        val arbeidstakerPersonident = PersonIdent(receivedSykmeldingDTO.personNrPasient)
        val hasExistingUbehandlet = connection.getPersonOppgaverByReferanseUuid(referanseUuid)
            .any { it.behandletTidspunkt == null }
        if (!hasExistingUbehandlet) {
            val personOppgave = PersonOppgave(
                referanseUuid = referanseUuid,
                personIdent = arbeidstakerPersonident,
                type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND,
                publish = true,
            )
            personOppgaveRepository.createPersonoppgave(
                personOppgave = personOppgave,
                connection = connection
            )
            val tiltakNav = !receivedSykmeldingDTO.sykmelding.tiltakNAV.isNullOrEmpty()
            val tiltakAndre = !receivedSykmeldingDTO.sykmelding.andreTiltak.isNullOrEmpty()
            log.info("Created personoppgave ${personOppgave.uuid} from sykmelding with tiltakNav=$tiltakNav and tiltakAndre=$tiltakAndre")
            COUNT_MOTTATT_SYKMELDING_SUCCESS.increment()
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(KafkaSykmeldingConsumer::class.java)
        val irrelevantSykmeldingFelterContent = listOf(".", "-", "nei")
    }
}

class ReceivedSykmeldingDTODeserializer : Deserializer<ReceivedSykmeldingDTO> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): ReceivedSykmeldingDTO =
        mapper.readValue(data, ReceivedSykmeldingDTO::class.java)
}
