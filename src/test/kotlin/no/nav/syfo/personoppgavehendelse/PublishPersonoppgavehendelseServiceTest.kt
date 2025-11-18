package no.nav.syfo.personoppgavehendelse

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.domain.PersonIdent
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.personoppgave.getPersonOppgaverByPublish
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.generators.generatePPersonoppgave
import no.nav.syfo.testutil.generators.generatePPersonoppgaver
import no.nav.syfo.testutil.generators.generatePersonoppgave
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

class PublishPersonoppgavehendelseServiceTest {
    private val ONE_DAY_AGO = OffsetDateTime.now().minusDays(1)
    private val TEN_DAYS_AGO = OffsetDateTime.now().minusDays(10)
    private lateinit var connection: Connection
    private lateinit var database: DatabaseInterface
    private lateinit var personOppgaveRepository: PersonOppgaveRepository
    private lateinit var personoppgavehendelseProducer: PersonoppgavehendelseProducer
    private lateinit var publishPersonoppgavehendelseService: PublishPersonoppgavehendelseService

    @BeforeEach
    fun setup() {
        connection = mockk(relaxed = true)
        database = mockk(relaxed = true)
        personOppgaveRepository = mockk()
        every { database.connection } returns connection
        personoppgavehendelseProducer = mockk(relaxed = true)
        publishPersonoppgavehendelseService = PublishPersonoppgavehendelseService(
            database = database,
            personoppgavehendelseProducer = personoppgavehendelseProducer,
            personOppgaveRepository = personOppgaveRepository,
        )
        mockkStatic("no.nav.syfo.personoppgave.GetPersonOppgaveQueriesKt")
        mockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
    }

    @AfterEach
    fun teardown() {
        clearMocks(connection, personoppgavehendelseProducer, personOppgaveRepository)
        unmockkStatic("no.nav.syfo.personoppgave.GetPersonOppgaveQueriesKt")
        unmockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
    }

    @Test
    fun `get unpublished oppgavehendelser returns oppgaver with publish true`() {
        val pPersonoppgaver = generatePPersonoppgaver()
        every { connection.getPersonOppgaverByPublish(publish = true) } returns pPersonoppgaver

        val unpublished = publishPersonoppgavehendelseService.getUnpublishedOppgaver()

        verify(exactly = 1) { connection.getPersonOppgaverByPublish(publish = true) }
        assertEquals(pPersonoppgaver.size, unpublished.size)
        assertEquals(pPersonoppgaver[0].uuid, unpublished[0].uuid)
    }

    @Test
    fun `doesn't publish an oppgave if newer exists`() {
        val moteUuid = UUID.randomUUID()
        val newerMoteUuid = UUID.randomUUID()
        val pPersonOppgave = generatePPersonoppgave(
            referanseUuid = moteUuid,
            sistEndret = TEN_DAYS_AGO.toLocalDateTime(),
        )
        val personoppgave = generatePersonoppgave().copy(
            uuid = pPersonOppgave.uuid,
            sistEndret = pPersonOppgave.sistEndret,
            referanseUuid = pPersonOppgave.referanseUuid,
            publish = true,
        )
        val newerPOppgave = generatePPersonoppgave(
            referanseUuid = newerMoteUuid,
            sistEndret = ONE_DAY_AGO.toLocalDateTime(),
        )
        every { connection.getPersonOppgaver(personoppgave.personIdent) } returns listOf(pPersonOppgave, newerPOppgave)
        justRun { personOppgaveRepository.updatePersonoppgaveBehandlet(any()) }

        publishPersonoppgavehendelseService.publish(personoppgave)

        verify(exactly = 0) { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
        verify(exactly = 1) { connection.getPersonOppgaver(personoppgave.personIdent) }
        val updatedPersonoppgaveSlot = slot<PersonOppgave>()
        verify(exactly = 1) { personOppgaveRepository.updatePersonoppgaveBehandlet(capture(updatedPersonoppgaveSlot)) }
        assertFalse(updatedPersonoppgaveSlot.captured.publish)
    }

    @Test
    fun `publishes an oppgavehendelse if the personoppgave is the newest`() {
        val olderMoteUuid = UUID.randomUUID()
        val moteUuid = UUID.randomUUID()
        val olderPPersonOppgave = generatePPersonoppgave(
            referanseUuid = olderMoteUuid,
            sistEndret = TEN_DAYS_AGO.toLocalDateTime(),
        )
        val pPersonOppgave = generatePPersonoppgave(
            referanseUuid = moteUuid,
            sistEndret = ONE_DAY_AGO.toLocalDateTime(),
        )
        val personOppgave = generatePersonoppgave().copy(
            uuid = pPersonOppgave.uuid,
            sistEndret = pPersonOppgave.sistEndret,
            referanseUuid = pPersonOppgave.referanseUuid,
            publish = true,
        )
        every { connection.getPersonOppgaver(PersonIdent(pPersonOppgave.fnr)) } returns listOf(olderPPersonOppgave, pPersonOppgave)
        justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
        justRun { personOppgaveRepository.updatePersonoppgaveBehandlet(any()) }

        publishPersonoppgavehendelseService.publish(personOppgave)

        verify(exactly = 1) { connection.getPersonOppgaver(personOppgave.personIdent) }
        verify(exactly = 1) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = PersonoppgavehendelseType.DIALOGMOTESVAR_MOTTATT,
                personIdent = personOppgave.personIdent,
                personoppgaveId = personOppgave.uuid,
            )
        }
        val updatedpersonoppgaveSlot = slot<PersonOppgave>()
        verify(exactly = 1) { personOppgaveRepository.updatePersonoppgaveBehandlet(capture(updatedpersonoppgaveSlot)) }
        val updatedPersonoppgave = updatedpersonoppgaveSlot.captured
        assertNotNull(updatedPersonoppgave.publishedAt)
        assertFalse(updatedPersonoppgave.publish)
        assertEquals(moteUuid, updatedPersonoppgave.referanseUuid)
        assertEquals(personOppgave.uuid, updatedPersonoppgave.uuid)
    }
}
