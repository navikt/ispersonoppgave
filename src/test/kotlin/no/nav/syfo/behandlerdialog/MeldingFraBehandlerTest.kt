package no.nav.syfo.behandlerdialog

import io.mockk.*
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.domain.PersonIdent
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKMeldingDTO
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import no.nav.syfo.util.Constants
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import java.util.*

class MeldingFraBehandlerTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaConsumer: KafkaConsumer<String, KMeldingDTO> = mockk(relaxed = true)
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer = mockk(relaxed = true)
    private val personOppgaveService = PersonOppgaveService(
        database = database,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
        personoppgaveRepository = PersonOppgaveRepository(database = database)
    )
    private val meldingFraBehandlerService = MeldingFraBehandlerService(
        database = database,
        personOppgaveService = personOppgaveService,
    )
    private val kafkaMeldingFraBehandler = KafkaMeldingFraBehandler(meldingFraBehandlerService = meldingFraBehandlerService)

    @BeforeEach
    fun setup() {
        clearMocks(personoppgavehendelseProducer, kafkaConsumer)
        every { kafkaConsumer.commitSync() } returns Unit
        justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
        database.dropData()
    }

    @Test
    fun `stores melding fra behandler from kafka in database and publish as new oppgave`() {
        val referanseUuid = UUID.randomUUID()
        val kMeldingFraBehandler = generateKMeldingDTO(referanseUuid)
        kafkaConsumer.mockPollConsumerRecords(recordValue = kMeldingFraBehandler)

        kafkaMeldingFraBehandler.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

        val personOppgave = database.connection.use { connection ->
            connection.getPersonOppgaverByReferanseUuid(referanseUuid = referanseUuid).map { it.toPersonOppgave() }.first()
        }
        assertFalse(personOppgave.publish)
        assertEquals(PersonOppgaveType.BEHANDLERDIALOG_SVAR.name, personOppgave.type.name)

        verify(exactly = 1) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT,
                personIdent = personOppgave.personIdent,
                personoppgaveId = personOppgave.uuid,
            )
        }
    }

    @Test
    fun `behandler ubesvart melding if svar received on same melding`() {
        val ubesvartMeldingService = UbesvartMeldingService(personOppgaveService)
        val kafkaUbesvartMelding = KafkaUbesvartMelding(database, ubesvartMeldingService)
        val referanseUuid = UUID.randomUUID()
        val kUbesvartMeldingDTO = generateKMeldingDTO(uuid = referanseUuid)
        val kMeldingFraBehandlerDTO = generateKMeldingDTO(parentRef = referanseUuid)

        kafkaConsumer.mockPollConsumerRecords(recordValue = kUbesvartMeldingDTO)
        kafkaUbesvartMelding.pollAndProcessRecords(kafkaConsumer)
        kafkaConsumer.mockPollConsumerRecords(recordValue = kMeldingFraBehandlerDTO)
        kafkaMeldingFraBehandler.pollAndProcessRecords(kafkaConsumer)

        val personoppgaveList = database.getPersonOppgaver(personIdent = PersonIdent(kUbesvartMeldingDTO.personIdent)).map { it.toPersonOppgave() }
        assertEquals(2, personoppgaveList.size)
        val personoppgaveUbesvart = personoppgaveList.first { it.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART }
        val personoppgaveSvar = personoppgaveList.first { it.type == PersonOppgaveType.BEHANDLERDIALOG_SVAR }
        assertNotNull(personoppgaveUbesvart.behandletTidspunkt)
        assertEquals(Constants.SYSTEM_VEILEDER_IDENT, personoppgaveUbesvart.behandletVeilederIdent)
        assertFalse(personoppgaveUbesvart.publish)
        assertNull(personoppgaveSvar.behandletTidspunkt)
        assertFalse(personoppgaveSvar.publish)

        verify(exactly = 1) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_MOTTATT,
                personIdent = personoppgaveUbesvart.personIdent,
                personoppgaveId = personoppgaveUbesvart.uuid,
            )
        }
        verify(exactly = 1) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_BEHANDLET,
                personIdent = personoppgaveUbesvart.personIdent,
                personoppgaveId = personoppgaveUbesvart.uuid,
            )
        }
        verify(exactly = 1) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT,
                personIdent = personoppgaveSvar.personIdent,
                personoppgaveId = personoppgaveSvar.uuid,
            )
        }
    }

    @Test
    fun `behandler ubesvart melding but does not publish when other ubehandlede ubesvart oppgaver exist`() {
        val ubesvartMeldingService = UbesvartMeldingService(personOppgaveService)
        val kafkaUbesvartMelding = KafkaUbesvartMelding(database, ubesvartMeldingService)
        val referanseUuid = UUID.randomUUID()
        val otherReferanseUuid = UUID.randomUUID()
        val kUbesvartMeldingDTO = generateKMeldingDTO(uuid = referanseUuid)
        val otherKUbesvartMeldingDTO = generateKMeldingDTO(uuid = otherReferanseUuid)
        val kMeldingFraBehandlerDTO = generateKMeldingDTO(parentRef = referanseUuid)

        kafkaConsumer.mockPollConsumerRecords(recordValue = kUbesvartMeldingDTO)
        kafkaUbesvartMelding.pollAndProcessRecords(kafkaConsumer)
        kafkaConsumer.mockPollConsumerRecords(recordValue = otherKUbesvartMeldingDTO)
        kafkaUbesvartMelding.pollAndProcessRecords(kafkaConsumer)
        kafkaConsumer.mockPollConsumerRecords(recordValue = kMeldingFraBehandlerDTO)
        kafkaMeldingFraBehandler.pollAndProcessRecords(kafkaConsumer)

        val personoppgaveList = database.getPersonOppgaver(personIdent = PersonIdent(kUbesvartMeldingDTO.personIdent)).map { it.toPersonOppgave() }
        assertEquals(3, personoppgaveList.size)
        val personoppgaveUbesvart = personoppgaveList.first { it.referanseUuid == referanseUuid }
        val otherPersonoppgaveUbesvart = personoppgaveList.first { it.referanseUuid == otherReferanseUuid }
        val personoppgaveSvar = personoppgaveList.first { it.type == PersonOppgaveType.BEHANDLERDIALOG_SVAR }
        assertNotNull(personoppgaveUbesvart.behandletTidspunkt)
        assertEquals(Constants.SYSTEM_VEILEDER_IDENT, personoppgaveUbesvart.behandletVeilederIdent)
        assertFalse(personoppgaveUbesvart.publish)
        assertNull(otherPersonoppgaveUbesvart.behandletTidspunkt)
        assertNull(otherPersonoppgaveUbesvart.behandletVeilederIdent)
        assertNull(personoppgaveSvar.behandletTidspunkt)
        assertFalse(personoppgaveSvar.publish)

        verify(exactly = 0) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_BEHANDLET,
                personIdent = personoppgaveUbesvart.personIdent,
                personoppgaveId = personoppgaveUbesvart.uuid,
            )
        }
        verify(exactly = 1) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT,
                personIdent = personoppgaveSvar.personIdent,
                personoppgaveId = personoppgaveSvar.uuid,
            )
        }
    }
}
