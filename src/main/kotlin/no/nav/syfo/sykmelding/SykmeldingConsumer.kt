package no.nav.syfo.behandler.kafka.sykmelding

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.*
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import org.apache.kafka.clients.consumer.*
import java.sql.Connection
import java.time.*
import java.util.*

const val SYKMELDING_TOPIC = "teamsykmelding.ok-sykmelding"

fun launchKafkaTaskSykmelding(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
) {
    val consumerProperties = kafkaAivenConsumerConfig<ReceivedSykmeldingDTO>(environment.kafka).apply {
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
    }
    val kafkaConsumerSykmelding = KafkaConsumer<String, ReceivedSykmeldingDTO>(consumerProperties)

    kafkaConsumerSykmelding.subscribe(
        listOf(SYKMELDING_TOPIC)
    )
    while (applicationState.ready) {
        pollAndProcessSykmelding(
            database = database,
            kafkaConsumerSykmelding = kafkaConsumerSykmelding,
        )
    }
}

fun pollAndProcessSykmelding(
    database: DatabaseInterface,
    kafkaConsumerSykmelding: KafkaConsumer<String, ReceivedSykmeldingDTO>,
) {
    val records = kafkaConsumerSykmelding.poll(Duration.ofMillis(1000))
    if (records.count() > 0) {
        processSykmelding(
            database = database,
            consumerRecords = records,
        )
        kafkaConsumerSykmelding.commitSync()
    }
}

fun processSykmelding(
    database: DatabaseInterface,
    consumerRecords: ConsumerRecords<String, ReceivedSykmeldingDTO>,
) {
    database.connection.use { connection ->
        consumerRecords.forEach {
            it.value()?.let { receivedSykmeldingDTO ->
                COUNT_MOTTATT_SYKMELDING.increment()
                if (receivedSykmeldingDTO.sykmelding.meldingTilNAV?.beskrivBistand != null) {
                    createPersonoppgave(
                        connection = connection,
                        receivedSykmeldingDTO = receivedSykmeldingDTO,
                    )
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
        connection.createPersonOppgave(
            referanseUuid = referanseUuid,
            personIdent = arbeidstakerPersonident,
            personOppgaveType = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND,
            publish = true,
        )
        COUNT_MOTTATT_SYKMELDING_SUCCESS.increment()
    }
}
