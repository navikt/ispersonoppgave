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
import no.nav.syfo.sykmelding.ReceivedSykmeldingDTO
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.serialization.Deserializer
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
                    if (!receivedSykmeldingDTO.sykmelding.meldingTilNAV?.beskrivBistand.isNullOrEmpty()) {
                        createPersonoppgave(
                            connection = connection,
                            receivedSykmeldingDTO = receivedSykmeldingDTO,
                        )
                    } else if (!receivedSykmeldingDTO.sykmelding.tiltakNAV.isNullOrEmpty()) {
                        COUNT_MOTTATT_SYKMELDING_TILTAK_NAV.increment()
                    } else if (!receivedSykmeldingDTO.sykmelding.andreTiltak.isNullOrEmpty()) {
                        COUNT_MOTTATT_SYKMELDING_TILTAK_ANDRE.increment()
                    } else if (receivedSykmeldingDTO.sykmelding.utdypendeOpplysninger.isNotEmpty()) {
                        COUNT_MOTTATT_SYKMELDING_UTDYPENDE.increment()
                        val utdypende = receivedSykmeldingDTO.sykmelding.utdypendeOpplysninger
                        if (utdypende.containsKey("6.3")) {
                            // ved 7 uker
                            COUNT_MOTTATT_SYKMELDING_UTDYPENDE_63.increment()
                        }
                        if (utdypende.containsKey("6.4")) {
                            // ved 17 uker
                            COUNT_MOTTATT_SYKMELDING_UTDYPENDE_64.increment()
                        }
                        if (utdypende.containsKey("6.5")) {
                            // ved 39 uker
                            COUNT_MOTTATT_SYKMELDING_UTDYPENDE_65.increment()
                        }
                    }
                }
            }
            connection.commit()
        }
    }

    private fun createPersonoppgave(
        connection: Connection,
        receivedSykmeldingDTO: ReceivedSykmeldingDTO,
    ) {
        val referanseUuid = UUID.fromString(receivedSykmeldingDTO.sykmelding.id)
        val arbeidstakerPersonident = PersonIdent(receivedSykmeldingDTO.personNrPasient)
        val hasExistingUbehandlet = connection.getPersonOppgaverByReferanseUuid(referanseUuid)
            .any { it.behandletTidspunkt == null }
        if (!hasExistingUbehandlet) {
            personOppgaveRepository.createPersonoppgave(
                personOppgave = PersonOppgave(
                    referanseUuid = referanseUuid,
                    personIdent = arbeidstakerPersonident,
                    type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND,
                    publish = true,
                ),
                connection = connection
            )
            COUNT_MOTTATT_SYKMELDING_SUCCESS.increment()
        }
    }
}

class ReceivedSykmeldingDTODeserializer : Deserializer<ReceivedSykmeldingDTO> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): ReceivedSykmeldingDTO =
        mapper.readValue(data, ReceivedSykmeldingDTO::class.java)
}
