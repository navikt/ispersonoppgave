package no.nav.syfo.dialogmotestatusendring

import io.ktor.util.*
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.dialogmotesvar.domain.KDialogmotesvar
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

@InternalAPI
object DialogmoteStatusendringServiceSpek : Spek({
    describe("Finish personoppgave when receiving an endring in dialogmotestatus") {
        val externalMockEnvironment = ExternalMockEnvironment()
        val database = externalMockEnvironment.database
        val dialogmoteUuid = UUID.randomUUID()
        val kDialogmotesvar = KDialogmotesvar(
            personident = UserConstants.ARBEIDSTAKER_FNR.value,
            type = DialogmoteSvartype.KOMMER_IKKE.name,
            dialogmoteUuid = dialogmoteUuid.toString(),
            virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER.value,
        )

        afterEachTest {
            database.connection.dropData()
        }

        it("Finish personoppgave when a dialogmote gets a referat") {
            database.connection.createPersonOppgave(
                kDialogmotesvar = kDialogmotesvar,
                type = PersonOppgaveType.DIALOGMOTESVAR,
            )
            val statusendring = getDialogmotestatusendring(DialogmoteStatusendringType.FERDIGSTILT, dialogmoteUuid)

            database.connection.use { connection ->
                processDialogmoteStatusendring(connection, statusendring)
                connection.commit()
            }

            val oppgaver = database.connection.getPersonOppgaveList(UserConstants.ARBEIDSTAKER_FNR)
            val dialogmotesvaroppgave = oppgaver[0]
            oppgaver.size shouldBeEqualTo 1
            dialogmotesvaroppgave.type shouldBeEqualTo PersonOppgaveType.DIALOGMOTESVAR.name
            dialogmotesvaroppgave.behandletTidspunkt shouldNotBe null
        }

        it("Finish personoppgave when a dialogmote changes place or time") {
            database.connection.createPersonOppgave(
                kDialogmotesvar = kDialogmotesvar,
                type = PersonOppgaveType.DIALOGMOTESVAR,
            )
            val statusendring = getDialogmotestatusendring(DialogmoteStatusendringType.NYTT_TID_STED, dialogmoteUuid)

            database.connection.use { connection ->
                processDialogmoteStatusendring(connection, statusendring)
                connection.commit()
            }

            val oppgaver = database.connection.getPersonOppgaveList(UserConstants.ARBEIDSTAKER_FNR)
            val dialogmotesvaroppgave = oppgaver[0]
            oppgaver.size shouldBeEqualTo 1
            dialogmotesvaroppgave.type shouldBeEqualTo PersonOppgaveType.DIALOGMOTESVAR.name
            dialogmotesvaroppgave.behandletTidspunkt shouldNotBe null
        }

        it("Finish personoppgave when a dialogmote is cancelled") {
            database.connection.createPersonOppgave(
                kDialogmotesvar = kDialogmotesvar,
                type = PersonOppgaveType.DIALOGMOTESVAR,
            )
            val statusendring = getDialogmotestatusendring(DialogmoteStatusendringType.AVLYST, dialogmoteUuid)

            database.connection.use { connection ->
                processDialogmoteStatusendring(connection, statusendring)
                connection.commit()
            }

            val oppgaver = database.connection.getPersonOppgaveList(UserConstants.ARBEIDSTAKER_FNR)
            val dialogmotesvaroppgave = oppgaver[0]
            oppgaver.size shouldBeEqualTo 1
            dialogmotesvaroppgave.type shouldBeEqualTo PersonOppgaveType.DIALOGMOTESVAR.name
            dialogmotesvaroppgave.behandletTidspunkt shouldNotBe null
        }

        it("Do nothing with personoppgave when a dialogmote is created") {
            val statusendring = getDialogmotestatusendring(DialogmoteStatusendringType.INNKALT, dialogmoteUuid)

            database.connection.use { connection ->
                processDialogmoteStatusendring(connection, statusendring)
                connection.commit()
            }

            val oppgaver = database.connection.getPersonOppgaveList(UserConstants.ARBEIDSTAKER_FNR)
            oppgaver.size shouldBeEqualTo 0
        }

        it("Do nothing if there is no personoppgave for dialogmøte") {
            // The case if statusendring is referat/endring/avlysning, and no negative responses to the innkalling
            val statusendring = getDialogmotestatusendring(DialogmoteStatusendringType.FERDIGSTILT, dialogmoteUuid)

            database.connection.use { connection ->
                processDialogmoteStatusendring(connection, statusendring)
                connection.commit()
            }

            val oppgaver = database.connection.getPersonOppgaveList(UserConstants.ARBEIDSTAKER_FNR)
            oppgaver.size shouldBeEqualTo 0
        }

        it("Add a personoppgavehendelse after a personoppgave has been closed") {
        }
    }
})
