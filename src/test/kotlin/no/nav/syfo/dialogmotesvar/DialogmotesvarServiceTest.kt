package no.nav.syfo.dialogmotesvar

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.dialogmotestatusendring.getDialogmoteStatusendring
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.dialogmotesvar.domain.SenderType
import no.nav.syfo.personoppgave.*
import no.nav.syfo.testutil.generators.generateDialogmotesvar
import no.nav.syfo.testutil.generators.generatePDialogmotestatusendring
import no.nav.syfo.testutil.generators.generatePPersonoppgave
import no.nav.syfo.testutil.generators.generatePersonoppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import org.junit.jupiter.api.*
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class DialogmotesvarServiceTest {
    private val ONE_DAY_AGO = OffsetDateTime.now().minusDays(1)
    private val TEN_DAYS_AGO = OffsetDateTime.now().minusDays(10)
    private val CUTOFF_DAYS_AGO = 100L
    private val CUTOFF_DATE = LocalDate.now().minusDays(CUTOFF_DAYS_AGO)
    private lateinit var connection: Connection

    @BeforeEach
    fun setup() {
        connection = mockk(relaxed = true)
        mockkStatic("no.nav.syfo.personoppgave.GetPersonOppgaveQueriesKt")
        mockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
        mockkStatic("no.nav.syfo.dialogmotestatusendring.DialogmoteStatusendringQueriesKt")
    }

    @AfterEach
    fun teardown() {
        clearMocks(connection)
        unmockkStatic("no.nav.syfo.personoppgave.GetPersonOppgaveQueriesKt")
        unmockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
        unmockkStatic("no.nav.syfo.dialogmotestatusendring.DialogmoteStatusendringQueriesKt")
    }

    @Test
    fun `creates an oppgave if arbeidstaker wants a new time or place for dialogmote`() {
        val moteUuid = UUID.randomUUID()
        every { connection.getPersonOppgaverByReferanseUuid(moteUuid) } returns emptyList()
        val dialogmotesvar = generateDialogmotesvar(moteuuid = moteUuid, svartype = DialogmoteSvartype.NYTT_TID_STED)
        val personOppgaveUuid = UUID.randomUUID()
        every { connection.createPersonOppgave(dialogmotesvar) } returns personOppgaveUuid
        processDialogmotesvar(connection, dialogmotesvar, CUTOFF_DATE)
        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmotesvar.moteuuid) }
        verify(exactly = 1) { connection.createPersonOppgave(dialogmotesvar) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `creates an oppgave if arbeidstaker does not want a dialogmote`() {
        val moteUuid = UUID.randomUUID()
        every { connection.getPersonOppgaverByReferanseUuid(moteUuid) } returns emptyList()
        val dialogmotesvar = generateDialogmotesvar(svartype = DialogmoteSvartype.KOMMER_IKKE)
        val personOppgaveUuid = UUID.randomUUID()
        every { connection.createPersonOppgave(dialogmotesvar) } returns personOppgaveUuid
        processDialogmotesvar(connection, dialogmotesvar, CUTOFF_DATE)
        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmotesvar.moteuuid) }
        verify(exactly = 1) { connection.createPersonOppgave(dialogmotesvar) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `creates an oppgave if behandler confirms attendance with non-empty svarTekst`() {
        val moteUuid = UUID.randomUUID()
        every { connection.getPersonOppgaverByReferanseUuid(moteUuid) } returns emptyList()
        val dialogmotesvar = generateDialogmotesvar(svartype = DialogmoteSvartype.KOMMER, svarTekst = "Passer bra?", senderType = SenderType.BEHANDLER)
        every { connection.createPersonOppgave(dialogmotesvar) } returns UUID.randomUUID()
        processDialogmotesvar(connection, dialogmotesvar, CUTOFF_DATE)
        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmotesvar.moteuuid) }
        verify(exactly = 1) { connection.createPersonOppgave(dialogmotesvar) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `does not create oppgave if behandler confirms attendance with empty svarTekst`() {
        val moteUuid = UUID.randomUUID()
        every { connection.getPersonOppgaverByReferanseUuid(moteUuid) } returns emptyList()
        val dialogmotesvar = generateDialogmotesvar(svartype = DialogmoteSvartype.KOMMER, senderType = SenderType.BEHANDLER)
        processDialogmotesvar(connection, dialogmotesvar, CUTOFF_DATE)
        verify(exactly = 0) { connection.getPersonOppgaverByReferanseUuid(any()) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `does not create oppgave if arbeidstaker confirms attendance with empty svarTekst`() {
        val moteUuid = UUID.randomUUID()
        every { connection.getPersonOppgaverByReferanseUuid(moteUuid) } returns emptyList()
        val dialogmotesvar = generateDialogmotesvar(svartype = DialogmoteSvartype.KOMMER)
        processDialogmotesvar(connection, dialogmotesvar, CUTOFF_DATE)
        verify(exactly = 0) { connection.getPersonOppgaverByReferanseUuid(any()) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `does not create oppgave if svar was sent before cutoff`() {
        val moteUuid = UUID.randomUUID()
        every { connection.getPersonOppgaverByReferanseUuid(moteUuid) } returns emptyList()
        val dialogmotesvar = generateDialogmotesvar(svartype = DialogmoteSvartype.KOMMER_IKKE, svarReceivedAt = OffsetDateTime.now().minusDays(CUTOFF_DAYS_AGO + 1))
        processDialogmotesvar(connection, dialogmotesvar, CUTOFF_DATE)
        verify(exactly = 0) { connection.getPersonOppgaverByReferanseUuid(any()) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `opens oppgave if møtesvar relevant and after sistEndret`() {
        val moteuuid = UUID.randomUUID()
        val newDialogmotesvar = generateDialogmotesvar(moteuuid = moteuuid, svartype = DialogmoteSvartype.NYTT_TID_STED, svarReceivedAt = OffsetDateTime.from(ONE_DAY_AGO))
        val pPersonoppgave = generatePPersonoppgave().copy(referanseUuid = moteuuid, sistEndret = TEN_DAYS_AGO.toLocalDateTimeOslo(), opprettet = TEN_DAYS_AGO.toLocalDateTime())
        val pDialogmoteStatusendring = generatePDialogmotestatusendring(type = DialogmoteStatusendringType.NYTT_TID_STED, uuid = moteuuid)
        every { connection.getPersonOppgaverByReferanseUuid(moteuuid) } returns listOf(pPersonoppgave)
        every { connection.getDialogmoteStatusendring(moteuuid) } returns mutableListOf(pDialogmoteStatusendring)
        justRun { connection.updatePersonoppgaveSetBehandlet(any()) }
        processDialogmotesvar(connection, newDialogmotesvar, CUTOFF_DATE)
        val updatePersonoppgave = generatePersonoppgave().copy(
            uuid = pPersonoppgave.uuid,
            referanseUuid = moteuuid,
            behandletTidspunkt = null,
            behandletVeilederIdent = null,
            opprettet = TEN_DAYS_AGO.toLocalDateTime(),
            sistEndret = newDialogmotesvar.svarReceivedAt.toLocalDateTimeOslo(),
            publish = true,
        )
        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(newDialogmotesvar.moteuuid) }
        verify(exactly = 1) { connection.getDialogmoteStatusendring(newDialogmotesvar.moteuuid) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 1) { connection.updatePersonoppgaveSetBehandlet(updatePersonoppgave) }
    }

    @Test
    fun `ignores møtesvar if happened before oppgave sistEndret`() {
        val moteuuid = UUID.randomUUID()
        val ppersonoppgave = generatePPersonoppgave().copy(referanseUuid = moteuuid, sistEndret = ONE_DAY_AGO.toLocalDateTime())
        val pDialogmoteStatusendring = generatePDialogmotestatusendring(type = DialogmoteStatusendringType.NYTT_TID_STED, uuid = moteuuid)
        every { connection.getPersonOppgaverByReferanseUuid(moteuuid) } returns listOf(ppersonoppgave)
        every { connection.getDialogmoteStatusendring(moteuuid) } returns mutableListOf(pDialogmoteStatusendring)
        val oldDialogmotesvar = generateDialogmotesvar(moteuuid = moteuuid, svartype = DialogmoteSvartype.NYTT_TID_STED, svarReceivedAt = TEN_DAYS_AGO)
        processDialogmotesvar(connection, oldDialogmotesvar, CUTOFF_DATE)
        verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(oldDialogmotesvar.moteuuid) }
        verify(exactly = 1) { connection.getDialogmoteStatusendring(oldDialogmotesvar.moteuuid) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }

    @Test
    fun `ignores møtesvar if current mote not active`() {
        val moteuuid = UUID.randomUUID()
        val newDialogmotesvar = generateDialogmotesvar(moteuuid = moteuuid, svartype = DialogmoteSvartype.NYTT_TID_STED, svarReceivedAt = OffsetDateTime.from(ONE_DAY_AGO))
        val pDialogmoteStatusendring = generatePDialogmotestatusendring(type = DialogmoteStatusendringType.AVLYST, uuid = moteuuid)
        every { connection.getDialogmoteStatusendring(moteuuid) } returns mutableListOf(pDialogmoteStatusendring)
        justRun { connection.updatePersonoppgaveSetBehandlet(any()) }
        processDialogmotesvar(connection, newDialogmotesvar, CUTOFF_DATE)
        verify(exactly = 0) { connection.getPersonOppgaverByReferanseUuid(newDialogmotesvar.moteuuid) }
        verify(exactly = 1) { connection.getDialogmoteStatusendring(newDialogmotesvar.moteuuid) }
        verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
        verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
    }
}
