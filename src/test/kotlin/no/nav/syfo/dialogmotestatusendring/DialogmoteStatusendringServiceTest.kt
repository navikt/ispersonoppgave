package no.nav.syfo.dialogmotestatusendring

import io.mockk.*
import no.nav.syfo.domain.DialogmoteStatusendringType
import no.nav.syfo.infrastructure.database.queries.createBehandletPersonoppgave
import no.nav.syfo.infrastructure.database.queries.getPersonOppgaverByReferanseUuid
import no.nav.syfo.infrastructure.database.queries.updatePersonoppgaveSetBehandlet
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.DialogmoteStatusendringConsumer
import no.nav.syfo.testutil.generators.generateDialogmotestatusendring
import no.nav.syfo.testutil.generators.generatePPersonoppgave
import no.nav.syfo.testutil.generators.generatePersonoppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import org.junit.jupiter.api.*
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

class DialogmoteStatusendringServiceTest {
    private val ONE_DAY_AGO = OffsetDateTime.now().minusDays(1)
    private val TEN_DAYS_AGO = OffsetDateTime.now().minusDays(10)
    private val HAPPENS_NOW = OffsetDateTime.now()
    private val GET_PERSONOPPGAVE_QUERIES_PATH = "no.nav.syfo.infrastructure.database.queries.GetPersonOppgaveQueriesKt"
    private val PERSONOPPGAVE_QUERIES_PATH = "no.nav.syfo.infrastructure.database.queries.PersonOppgaveQueriesKt"
    private lateinit var dialogmoteUuid: UUID
    private lateinit var connection: Connection

    @BeforeEach
    fun setup() {
        dialogmoteUuid = UUID.randomUUID()
        connection = mockk(relaxed = true)
        mockkStatic(GET_PERSONOPPGAVE_QUERIES_PATH)
        mockkStatic(PERSONOPPGAVE_QUERIES_PATH)
    }

    @AfterEach
    fun teardown() {
        clearMocks(connection)
        unmockkStatic(GET_PERSONOPPGAVE_QUERIES_PATH)
        unmockkStatic(PERSONOPPGAVE_QUERIES_PATH)
    }

    @Test
    fun `Finish personoppgave when a dialogmote gets a referat`() {
        val statusendring = generateDialogmotestatusendring(
            DialogmoteStatusendringType.FERDIGSTILT,
            dialogmoteUuid,
            HAPPENS_NOW,
        )
        val personoppgave = generatePPersonoppgave(dialogmoteUuid, ONE_DAY_AGO.toLocalDateTime()).copy(
            opprettet = TEN_DAYS_AGO.toLocalDateTimeOslo(),
        )
        val updatePersonoppgave = generatePersonoppgave().copy(
            uuid = personoppgave.uuid,
            referanseUuid = dialogmoteUuid,
            behandletTidspunkt = statusendring.endringTidspunkt.toLocalDateTimeOslo(),
            behandletVeilederIdent = statusendring.veilederIdent,
            opprettet = TEN_DAYS_AGO.toLocalDateTimeOslo(),
            sistEndret = HAPPENS_NOW.toLocalDateTimeOslo(),
            publish = true,
        )
        every { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) } returns listOf(personoppgave)
        justRun { connection.updatePersonoppgaveSetBehandlet(any()) }

        DialogmoteStatusendringConsumer.processDialogmoteStatusendring(connection, statusendring)

        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) }
        verify(exactly = 1) { connection.updatePersonoppgaveSetBehandlet(updatePersonoppgave) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
    }

    @Test
    fun `Finish personoppgave when a dialogmote changes place or time`() {
        val statusendring = generateDialogmotestatusendring(DialogmoteStatusendringType.NYTT_TID_STED, dialogmoteUuid, HAPPENS_NOW)
        val personoppgave = generatePPersonoppgave(dialogmoteUuid, ONE_DAY_AGO.toLocalDateTime()).copy(
            opprettet = TEN_DAYS_AGO.toLocalDateTimeOslo(),
        )
        val updatePersonoppgave = generatePersonoppgave().copy(
            uuid = personoppgave.uuid,
            referanseUuid = dialogmoteUuid,
            behandletTidspunkt = statusendring.endringTidspunkt.toLocalDateTimeOslo(),
            behandletVeilederIdent = statusendring.veilederIdent,
            opprettet = TEN_DAYS_AGO.toLocalDateTimeOslo(),
            sistEndret = HAPPENS_NOW.toLocalDateTimeOslo(),
            publish = true,
        )
        every { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) } returns listOf(personoppgave)
        justRun { connection.updatePersonoppgaveSetBehandlet(any()) }

        DialogmoteStatusendringConsumer.processDialogmoteStatusendring(connection, statusendring)

        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) }
        verify(exactly = 1) { connection.updatePersonoppgaveSetBehandlet(updatePersonoppgave) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
    }

    @Test
    fun `Finish personoppgave when a dialogmote is cancelled`() {
        val statusendring = generateDialogmotestatusendring(DialogmoteStatusendringType.AVLYST, dialogmoteUuid, HAPPENS_NOW)
        val personoppgave = generatePPersonoppgave(dialogmoteUuid, ONE_DAY_AGO.toLocalDateTime()).copy(
            opprettet = TEN_DAYS_AGO.toLocalDateTimeOslo(),
        )
        val updatePersonoppgave = generatePersonoppgave().copy(
            uuid = personoppgave.uuid,
            referanseUuid = dialogmoteUuid,
            behandletTidspunkt = statusendring.endringTidspunkt.toLocalDateTimeOslo(),
            behandletVeilederIdent = statusendring.veilederIdent,
            opprettet = TEN_DAYS_AGO.toLocalDateTimeOslo(),
            sistEndret = HAPPENS_NOW.toLocalDateTimeOslo(),
            publish = true,
        )
        every { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) } returns listOf(personoppgave)
        justRun { connection.updatePersonoppgaveSetBehandlet(any()) }

        DialogmoteStatusendringConsumer.processDialogmoteStatusendring(connection, statusendring)

        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) }
        verify(exactly = 1) { connection.updatePersonoppgaveSetBehandlet(updatePersonoppgave) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
    }

    @Test
    fun `Create finished personoppgave when dialogmote is created and no oppgave exists`() {
        val statusendring = generateDialogmotestatusendring(DialogmoteStatusendringType.INNKALT, dialogmoteUuid, HAPPENS_NOW)
        every { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) } returns emptyList()
        justRun { connection.createBehandletPersonoppgave(statusendring, any()) }

        DialogmoteStatusendringConsumer.processDialogmoteStatusendring(connection, statusendring)

        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) }
        verify(exactly = 1) { connection.createBehandletPersonoppgave(statusendring, any()) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `Do nothing if dialogmote created happened before personoppgave sist endret`() {
        val statusendring = generateDialogmotestatusendring(DialogmoteStatusendringType.INNKALT, dialogmoteUuid, ONE_DAY_AGO)
        val personoppgave = generatePPersonoppgave(dialogmoteUuid, HAPPENS_NOW.toLocalDateTime())
        every { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) } returns listOf(personoppgave)

        DialogmoteStatusendringConsumer.processDialogmoteStatusendring(connection, statusendring)

        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `Do nothing if dialogmote moved status happened før personoppgave sist endret`() {
        val statusendring = generateDialogmotestatusendring(DialogmoteStatusendringType.NYTT_TID_STED, dialogmoteUuid, ONE_DAY_AGO)
        val personoppgave = generatePPersonoppgave(dialogmoteUuid, HAPPENS_NOW.toLocalDateTime())
        every { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) } returns listOf(personoppgave)

        DialogmoteStatusendringConsumer.processDialogmoteStatusendring(connection, statusendring)

        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `Close oppgave if dialogmote finished before personoppgave sist endret`() {
        val statusendring = generateDialogmotestatusendring(DialogmoteStatusendringType.AVLYST, dialogmoteUuid, ONE_DAY_AGO)
        val personoppgave = generatePPersonoppgave(dialogmoteUuid, HAPPENS_NOW.toLocalDateTime())
        every { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) } returns listOf(personoppgave)
        justRun { connection.updatePersonoppgaveSetBehandlet(any()) }

        DialogmoteStatusendringConsumer.processDialogmoteStatusendring(connection, statusendring)

        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmoteUuid) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 1) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }
}
