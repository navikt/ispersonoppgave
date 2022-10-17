package no.nav.syfo.dialogmotesvar

import io.mockk.*
import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.generateDialogmotesvar
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Connection
import java.time.LocalDateTime
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
                verify(exactly = 0) { connection.updateDialogmotesvarOppgaveSetUbehandlet(any()) }
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
                verify(exactly = 0) { connection.updateDialogmotesvarOppgaveSetUbehandlet(any()) }
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
                verify(exactly = 0) { connection.updateDialogmotesvarOppgaveSetUbehandlet(any()) }
            }
        }

        describe("Given that an oppgave exists for the dialogmote") {

            it("opens oppgave if actionable møtesvar happened after oppgave was sist endret") {
                val moteuuid = UUID.randomUUID()
                every { connection.getPersonOppgaveByReferanseUuid(moteuuid) } returns PPersonOppgave(
                    id = 1,
                    uuid = UUID.randomUUID(),
                    referanseUuid = moteuuid,
                    fnr = UserConstants.ARBEIDSTAKER_FNR.value,
                    virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER.value,
                    type = PersonOppgaveType.DIALOGMOTESVAR.name,
                    oversikthendelseTidspunkt = null,
                    behandletTidspunkt = null,
                    behandletVeilederIdent = null,
                    opprettet = LocalDateTime.now(),
                    sistEndret = TEN_DAYS_AGO.toLocalDateTime(),
                )
                val newDialogmotesvar = generateDialogmotesvar(
                    moteuuid = moteuuid,
                    svartype = DialogmoteSvartype.NYTT_TID_STED,
                    svarReceivedAt = OffsetDateTime.from(ONE_DAY_AGO),
                )
                justRun { connection.updateDialogmotesvarOppgaveSetUbehandlet(dialogmotesvar = newDialogmotesvar) }

                processDialogmotesvar(
                    connection = connection,
                    dialogmotesvar = newDialogmotesvar,
                )

                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 1) { connection.updateDialogmotesvarOppgaveSetUbehandlet(newDialogmotesvar) }
            }

            it("ignores actionable møtesvar if it happened before the oppgave was sist endret") {
                val moteuuid = UUID.randomUUID()
                every { connection.getPersonOppgaveByReferanseUuid(moteuuid) } returns PPersonOppgave(
                    id = 1,
                    uuid = UUID.randomUUID(),
                    referanseUuid = moteuuid,
                    fnr = UserConstants.ARBEIDSTAKER_FNR.value,
                    virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER.value,
                    type = PersonOppgaveType.DIALOGMOTESVAR.name,
                    oversikthendelseTidspunkt = null,
                    behandletTidspunkt = null,
                    behandletVeilederIdent = null,
                    opprettet = LocalDateTime.now(),
                    sistEndret = ONE_DAY_AGO.toLocalDateTime(),
                )
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
                verify(exactly = 0) { connection.updateDialogmotesvarOppgaveSetUbehandlet(any()) }
            }
        }
    }
})
