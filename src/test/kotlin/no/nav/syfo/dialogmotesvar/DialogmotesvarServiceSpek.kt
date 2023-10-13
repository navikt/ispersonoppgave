package no.nav.syfo.dialogmotesvar

import io.mockk.*
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.dialogmotestatusendring.getDialogmoteStatusendring
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.personoppgave.*
import no.nav.syfo.testutil.generators.generateDialogmotesvar
import no.nav.syfo.testutil.generators.generatePDialogmotestatusendring
import no.nav.syfo.testutil.generators.generatePPersonoppgave
import no.nav.syfo.testutil.generators.generatePersonoppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class DialogmotesvarServiceSpek : Spek({

    val ONE_DAY_AGO = OffsetDateTime.now().minusDays(1)
    val TEN_DAYS_AGO = OffsetDateTime.now().minusDays(10)
    val CUTOFF_DAYS_AGO = 100L
    val CUTOFF_DATE = LocalDate.now().minusDays(CUTOFF_DAYS_AGO)

    describe("Manage oppgaver based on dialogmotesvar") {
        val connection = mockk<Connection>(relaxed = true)

        beforeEachTest {
            mockkStatic("no.nav.syfo.personoppgave.GetPersonOppgaveQueriesKt")
            mockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
            mockkStatic("no.nav.syfo.dialogmotestatusendring.DialogmoteStatusendringQueriesKt")
        }

        afterEachTest {
            clearMocks(connection)
            unmockkStatic("no.nav.syfo.personoppgave.GetPersonOppgaveQueriesKt")
            unmockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
        }

        describe("Given that there is no existing oppgave") {
            val moteUuid = UUID.randomUUID()

            beforeEachTest {
                every { connection.getPersonOppgaverByReferanseUuid(moteUuid) } returns emptyList()
            }

            it("creates an oppgave if arbeidstaker wants a new time or place for dialogmote") {
                val dialogmotesvar = generateDialogmotesvar(
                    moteuuid = moteUuid,
                    svartype = DialogmoteSvartype.NYTT_TID_STED,
                )
                val personOppgaveUuid = UUID.randomUUID()
                every { connection.createPersonOppgave(dialogmotesvar) } returns personOppgaveUuid

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                    cutoffDate = CUTOFF_DATE,
                )

                verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmotesvar.moteuuid) }
                verify(exactly = 1) { connection.createPersonOppgave(dialogmotesvar) }
                verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
            }

            it("creates an oppgave if arbeidstaker does not want a dialogmote") {
                val dialogmotesvar = generateDialogmotesvar(
                    svartype = DialogmoteSvartype.KOMMER_IKKE,
                )
                val personOppgaveUuid = UUID.randomUUID()
                every { connection.createPersonOppgave(dialogmotesvar) } returns personOppgaveUuid

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                    cutoffDate = CUTOFF_DATE,
                )

                verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(dialogmotesvar.moteuuid) }
                verify(exactly = 1) { connection.createPersonOppgave(dialogmotesvar) }
                verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
            }

            it("does not create an oppgave if arbeidstaker confirms attendance to dialogmote") {
                val dialogmotesvar = generateDialogmotesvar(
                    svartype = DialogmoteSvartype.KOMMER,
                )

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                    cutoffDate = CUTOFF_DATE,
                )

                verify(exactly = 0) { connection.getPersonOppgaverByReferanseUuid(any()) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
            }

            it("does not create an oppgave if svar was sent before cutoff date") {
                val dialogmotesvar = generateDialogmotesvar(
                    svartype = DialogmoteSvartype.KOMMER_IKKE,
                    svarReceivedAt = OffsetDateTime.now().minusDays(CUTOFF_DAYS_AGO + 1),
                )

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                    cutoffDate = CUTOFF_DATE,
                )

                verify(exactly = 0) { connection.getPersonOppgaverByReferanseUuid(any()) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
            }
        }

        describe("Given that an oppgave exists for the dialogmote") {

            it("opens oppgave if møtesvar is relevant to veileder and happened after oppgave was sist endret") {
                val moteuuid = UUID.randomUUID()
                val newDialogmotesvar = generateDialogmotesvar(
                    moteuuid = moteuuid,
                    svartype = DialogmoteSvartype.NYTT_TID_STED,
                    svarReceivedAt = OffsetDateTime.from(ONE_DAY_AGO),
                )
                val pPersonoppgave = generatePPersonoppgave().copy(
                    referanseUuid = moteuuid,
                    sistEndret = TEN_DAYS_AGO.toLocalDateTimeOslo(),
                    opprettet = TEN_DAYS_AGO.toLocalDateTime(),
                )
                val pDialogmoteStatusendring = generatePDialogmotestatusendring(
                    type = DialogmoteStatusendringType.NYTT_TID_STED,
                    uuid = moteuuid,
                )
                every { connection.getPersonOppgaverByReferanseUuid(moteuuid) } returns listOf(pPersonoppgave)
                every { connection.getDialogmoteStatusendring(moteuuid) } returns mutableListOf(pDialogmoteStatusendring)
                justRun { connection.updatePersonoppgaveSetBehandlet(any()) }

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = newDialogmotesvar,
                    cutoffDate = CUTOFF_DATE,
                )

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

            it("ignores møtesvar for veileder if it happened before the oppgave was sist endret") {
                val moteuuid = UUID.randomUUID()
                val ppersonoppgave = generatePPersonoppgave().copy(
                    referanseUuid = moteuuid,
                    sistEndret = ONE_DAY_AGO.toLocalDateTime(),
                )
                val pDialogmoteStatusendring = generatePDialogmotestatusendring(
                    type = DialogmoteStatusendringType.NYTT_TID_STED,
                    uuid = moteuuid,
                )
                every { connection.getPersonOppgaverByReferanseUuid(moteuuid) } returns listOf(ppersonoppgave)
                every { connection.getDialogmoteStatusendring(moteuuid) } returns mutableListOf(pDialogmoteStatusendring)
                val oldDialogmotesvar = generateDialogmotesvar(
                    moteuuid = moteuuid,
                    svartype = DialogmoteSvartype.NYTT_TID_STED,
                    svarReceivedAt = TEN_DAYS_AGO,
                )

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = oldDialogmotesvar,
                    cutoffDate = CUTOFF_DATE,
                )

                verify(exactly = 1) { connection.getPersonOppgaverByReferanseUuid(oldDialogmotesvar.moteuuid) }
                verify(exactly = 1) { connection.getDialogmoteStatusendring(oldDialogmotesvar.moteuuid) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
            }

            it("ignores møtesvar for veileder if current mote is not active") {
                val moteuuid = UUID.randomUUID()
                val newDialogmotesvar = generateDialogmotesvar(
                    moteuuid = moteuuid,
                    svartype = DialogmoteSvartype.NYTT_TID_STED,
                    svarReceivedAt = OffsetDateTime.from(ONE_DAY_AGO),
                )
                val pDialogmoteStatusendring = generatePDialogmotestatusendring(
                    type = DialogmoteStatusendringType.AVLYST,
                    uuid = moteuuid,
                )
                every { connection.getDialogmoteStatusendring(moteuuid) } returns mutableListOf(pDialogmoteStatusendring)
                justRun { connection.updatePersonoppgaveSetBehandlet(any()) }

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = newDialogmotesvar,
                    cutoffDate = CUTOFF_DATE,
                )

                verify(exactly = 0) { connection.getPersonOppgaverByReferanseUuid(newDialogmotesvar.moteuuid) }
                verify(exactly = 1) { connection.getDialogmoteStatusendring(newDialogmotesvar.moteuuid) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 0) { connection.updatePersonoppgaveSetBehandlet(any()) }
            }
        }
    }
})
