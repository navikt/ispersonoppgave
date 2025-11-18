package no.nav.syfo.behandlerdialog

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.AvvistMeldingConsumerService
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.createPersonOppgave
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKMeldingDTO
import no.nav.syfo.testutil.generators.generatePersonoppgave
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import no.nav.syfo.util.Constants
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import java.util.*
import java.util.concurrent.Future

class AvvistMeldingTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private lateinit var database: no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
    private lateinit var kafkaConsumer: KafkaConsumer<String, KMeldingDTO>
    private lateinit var producer: KafkaProducer<String, KPersonoppgavehendelse>
    private lateinit var personoppgavehendelseProducer: PersonoppgavehendelseProducer
    private lateinit var personOppgaveService: PersonOppgaveService
    private lateinit var avvistMeldingService: AvvistMeldingService
    private lateinit var avvistMeldingConsumerService: AvvistMeldingConsumerService

    @BeforeEach
    fun setup() {
        database = externalMockEnvironment.database
        kafkaConsumer = mockk(relaxed = true)
        producer = mockk()
        personoppgavehendelseProducer = PersonoppgavehendelseProducer(producer)
        personOppgaveService = PersonOppgaveService(
            database = database,
            personoppgavehendelseProducer = personoppgavehendelseProducer,
            personoppgaveRepository = PersonOppgaveRepository(database = database)
        )
        avvistMeldingService = AvvistMeldingService(database, personOppgaveService)
        avvistMeldingConsumerService = AvvistMeldingConsumerService(avvistMeldingService)
        every { kafkaConsumer.commitSync() } returns Unit
        coEvery { producer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        database.dropData()
        clearMocks(producer, kafkaConsumer)
    }

    @Test
    fun `stores avvist melding from kafka as oppgave in database and publish as new oppgave`() {
        val referanseUuid = UUID.randomUUID()
        val kMeldingDTO = generateKMeldingDTO(referanseUuid)
        kafkaConsumer.mockPollConsumerRecords(recordValue = kMeldingDTO)

        avvistMeldingConsumerService.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

        val personOppgave = database.connection.use { connection ->
            connection.getPersonOppgaverByReferanseUuid(referanseUuid = referanseUuid).map { it.toPersonOppgave() }.first()
        }
        assertFalse(personOppgave.publish)
        assertEquals(PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST.name, personOppgave.type.name)

        val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { producer.send(capture(producerRecordSlot)) }

        val kPersonoppgavehendelse = producerRecordSlot.captured.value()
        assertEquals(PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_MOTTATT.name, kPersonoppgavehendelse.hendelsetype)
        assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, kPersonoppgavehendelse.personident)
    }

    @Test
    fun `stores avvist melding and also publish when ubesvartoppgave exists for same referanseUuid`() {
        val referanseUuid = UUID.randomUUID()
        val paaminnelsesOppgaveUUID = UUID.randomUUID()
        database.connection.use { connection ->
            connection.createPersonOppgave(
                personoppgave = generatePersonoppgave(
                    uuid = paaminnelsesOppgaveUUID,
                    referanseUuid = referanseUuid,
                    type = PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART,
                    virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER,
                )
            )
            connection.commit()
        }
        val kMeldingDTO = generateKMeldingDTO(referanseUuid)
        kafkaConsumer.mockPollConsumerRecords(recordValue = kMeldingDTO)

        avvistMeldingConsumerService.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

        val personOppgave = database.connection.use { connection ->
            connection.getPersonOppgaverByReferanseUuid(referanseUuid = referanseUuid)
                .map { it.toPersonOppgave() }
                .first { it.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST }
        }
        assertFalse(personOppgave.publish)
        assertEquals(PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST.name, personOppgave.type.name)

        val producerRecordSlot = mutableListOf<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 2) { producer.send(capture(producerRecordSlot)) }

        val kPersonoppgavehendelse = producerRecordSlot.first().value()
        assertEquals(PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_MOTTATT.name, kPersonoppgavehendelse.hendelsetype)
        assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, kPersonoppgavehendelse.personident)

        val paaminnelsesOppgave = personOppgaveService.getPersonOppgave(paaminnelsesOppgaveUUID)
        assertNotNull(paaminnelsesOppgave!!.behandletTidspunkt)
        assertEquals(Constants.SYSTEM_VEILEDER_IDENT, paaminnelsesOppgave.behandletVeilederIdent)
    }

    @Test
    fun `stores avvist melding but does not publish other ubesvart oppgave exists for same person`() {
        val referanseUuid = UUID.randomUUID()
        val paaminnelsesOppgaveUUID = UUID.randomUUID()
        database.connection.use { connection ->
            connection.createPersonOppgave(
                personoppgave = generatePersonoppgave(
                    uuid = paaminnelsesOppgaveUUID,
                    referanseUuid = referanseUuid,
                    type = PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART,
                    virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER,
                )
            )
            connection.createPersonOppgave(
                personoppgave = generatePersonoppgave(
                    type = PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART,
                    virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER,
                )
            )
            connection.commit()
        }
        val kMeldingDTO = generateKMeldingDTO(referanseUuid)
        kafkaConsumer.mockPollConsumerRecords(recordValue = kMeldingDTO)

        avvistMeldingConsumerService.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

        val personOppgave = database.connection.use { connection ->
            connection.getPersonOppgaverByReferanseUuid(referanseUuid = referanseUuid)
                .map { it.toPersonOppgave() }
                .first { it.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST }
        }
        assertFalse(personOppgave.publish)
        assertEquals(PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST.name, personOppgave.type.name)

        val producerRecordSlot = mutableListOf<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { producer.send(capture(producerRecordSlot)) }

        val kPersonoppgavehendelse = producerRecordSlot.first().value()
        assertEquals(PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_MOTTATT.name, kPersonoppgavehendelse.hendelsetype)
        assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, kPersonoppgavehendelse.personident)

        val paaminnelsesOppgave = personOppgaveService.getPersonOppgave(paaminnelsesOppgaveUUID)
        assertNotNull(paaminnelsesOppgave!!.behandletTidspunkt)
        assertEquals(Constants.SYSTEM_VEILEDER_IDENT, paaminnelsesOppgave.behandletVeilederIdent)
    }

    @Test
    fun `will not store avvist melding when value is null tombstone`() {
        kafkaConsumer.mockPollConsumerRecords(recordValue = null)
        avvistMeldingConsumerService.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

        val pPersonOppgaver = database.getPersonOppgaver(personIdent = UserConstants.ARBEIDSTAKER_FNR)
        assertEquals(0, pPersonOppgaver.size)

        verify(exactly = 0) { producer.send(any()) }
    }
}
