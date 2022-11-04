package no.nav.syfo.dialogmotesvar

import io.mockk.*
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.personoppgave.*
import no.nav.syfo.testutil.*
import no.nav.syfo.util.toLocalDateTimeOslo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

class DialogmotesvarServiceSpek : Spek({

    val ONE_DAY_AGO = OffsetDateTime.now().minusDays(1)
    val TEN_DAYS_AGO = OffsetDateTime.now().minusDays(10)

    describe("Manage oppgaver based on dialogmotesvar") {
        val connection = mockk<Connection>(relaxed = true)

        beforeEachTest {
            mockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
        }

        afterEachTest {
            clearMocks(connection)
            unmockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
        }

        describe("Given that there is no existing oppgave") {
            val moteUuid = UUID.randomUUID()

            beforeEachTest {
                every { connection.getPersonOppgaveByReferanseUuid(moteUuid) } returns null
            }

            it("creates an oppgave if arbeidstaker wants a new time or place for dialogmote") {
                val dialogmotesvar = generateDialogmotesvar(
                    moteuuid = moteUuid,
                    svartype = DialogmoteSvartype.NYTT_TID_STED,
                )
                justRun { connection.createPersonOppgave(dialogmotesvar, any()) }

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                )

                verify(exactly = 1) { connection.createPersonOppgave(dialogmotesvar, any()) }
                verify(exactly = 0) { connection.updatePersonoppgave(any()) }
            }

            it("creates an oppgave if arbeidstaker does not want a dialogmote") {
                val dialogmotesvar = generateDialogmotesvar(
                    svartype = DialogmoteSvartype.KOMMER_IKKE,
                )
                justRun { connection.createPersonOppgave(dialogmotesvar, any()) }

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                )

                verify(exactly = 1) { connection.createPersonOppgave(dialogmotesvar, any()) }
                verify(exactly = 0) { connection.updatePersonoppgave(any()) }
            }

            it("does not create an oppgave if arbeidstaker confirms attendance to dialogmote") {
                val dialogmotesvar = generateDialogmotesvar(
                    svartype = DialogmoteSvartype.KOMMER,
                )

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = dialogmotesvar,
                )

                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 0) { connection.updatePersonoppgave(any()) }
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
                every { connection.getPersonOppgaveByReferanseUuid(moteuuid) } returns pPersonoppgave
                justRun { connection.updatePersonoppgave(any()) }

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = newDialogmotesvar,
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
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 1) { connection.updatePersonoppgave(updatePersonoppgave) }
            }

            it("ignores relevant møtesvar for veileder if it happened before the oppgave was sist endret") {
                val moteuuid = UUID.randomUUID()
                val ppersonoppgave = generatePPersonoppgave().copy(
                    referanseUuid = moteuuid,
                    sistEndret = ONE_DAY_AGO.toLocalDateTime(),
                )
                every { connection.getPersonOppgaveByReferanseUuid(moteuuid) } returns ppersonoppgave
                val oldDialogmotesvar = generateDialogmotesvar(
                    moteuuid = moteuuid,
                    svartype = DialogmoteSvartype.NYTT_TID_STED,
                    svarReceivedAt = TEN_DAYS_AGO,
                )

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = oldDialogmotesvar,
                )

                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 0) { connection.updatePersonoppgave(any()) }
            }
        }
    }
})
