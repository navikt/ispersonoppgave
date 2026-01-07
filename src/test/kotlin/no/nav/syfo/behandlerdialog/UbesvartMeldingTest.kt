package no.nav.syfo.behandlerdialog

import io.mockk.*
import no.nav.syfo.infrastructure.kafka.behandlerdialog.KMeldingDTO
import no.nav.syfo.infrastructure.kafka.behandlerdialog.KafkaUbesvartMelding
import no.nav.syfo.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.application.PersonOppgaveService
import no.nav.syfo.application.UbesvartMeldingService
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.infrastructure.database.queries.toPersonOppgave
import no.nav.syfo.infrastructure.database.queries.getPersonOppgaverByReferanseUuid
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKMeldingDTO
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import java.util.*

class UbesvartMeldingTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaConsumer: KafkaConsumer<String, KMeldingDTO> = mockk(relaxed = true)
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer = mockk(relaxed = true)
    private val personOppgaveService = PersonOppgaveService(
        database = database,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
        personoppgaveRepository = PersonOppgaveRepository(database = database)
    )
    private val ubesvartMeldingService = UbesvartMeldingService(personOppgaveService)
    private val kafkaUbesvartMelding = KafkaUbesvartMelding(database, ubesvartMeldingService)

    @BeforeEach
    fun setup() {
        clearMocks(kafkaConsumer)
        every { kafkaConsumer.commitSync() } returns Unit
        database.dropData()
    }

    @AfterEach
    fun teardown() {
        database.dropData()
        clearMocks(personoppgavehendelseProducer)
    }

    @Test
    fun `stores ubesvart melding from kafka as oppgave in database and publish as new oppgave`() {
        val referanseUuid = UUID.randomUUID()
        val kMeldingDTO = generateKMeldingDTO(referanseUuid)
        kafkaConsumer.mockPollConsumerRecords(recordValue = kMeldingDTO)
        justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }

        kafkaUbesvartMelding.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

        val personOppgave = database.connection.use { connection ->
            connection.getPersonOppgaverByReferanseUuid(referanseUuid = referanseUuid).map { it.toPersonOppgave() }.first()
        }
        assertFalse(personOppgave.publish)
        assertEquals(PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART.name, personOppgave.type.name)

        verify(exactly = 1) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_MOTTATT,
                personIdent = personOppgave.personIdent,
                personoppgaveId = personOppgave.uuid,
            )
        }
    }
}
