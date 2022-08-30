package no.nav.syfo.dialogmotestatusendring

import io.ktor.util.*
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.dialogmotesvar.domain.KDialogmotesvar
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
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

        it("Finish personoppgave when dialogmote has a referat") {
            database.connection.createPersonOppgave(
                kDialogmotesvar = kDialogmotesvar,
                type = PersonOppgaveType.DIALOGMOTESVAR,
            )
            val dialogmoteStatusendring = DialogmoteStatusendring(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
                type = DialogmoteStatusendringType.FERDIGSTILT,
                endringTidspunkt = OffsetDateTime.now(),
                dialogmoteUuid = dialogmoteUuid,
                veilederIdent = UserConstants.VEILEDER_IDENT,
            )

            database.connection.use { connection ->
                processDialogmoteStatusendring(connection, dialogmoteStatusendring)
                connection.commit()
            }

            val oppgaver = database.connection.getPersonOppgaveList(UserConstants.ARBEIDSTAKER_FNR)
            val dialogmotesvaroppgave = oppgaver[0]
            oppgaver.size shouldBeEqualTo 1
            dialogmotesvaroppgave.type shouldBeEqualTo PersonOppgaveType.DIALOGMOTESVAR.name
            dialogmotesvaroppgave.behandletTidspunkt shouldNotBe null
        }

        it("Finish personoppgave when dialogmote is changing place or time") {
        }

        it("Finish personoppgave when dialogmote is cancelled") {
        }

        it("Do nothing with personoppgave when a dialogmote has been created") {
        }

        it("Do nothing if there is no personoppgave for dialogmøte") {
            // The case if statusendring is referat/endring/avlysning, and no one has bothered to rsvp the innkalling
            val dialogmoteStatusendring = DialogmoteStatusendring(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
                type = DialogmoteStatusendringType.FERDIGSTILT,
                endringTidspunkt = OffsetDateTime.now(),
                dialogmoteUuid = dialogmoteUuid,
                veilederIdent = UserConstants.VEILEDER_IDENT,
            )

            database.connection.use { connection ->
                processDialogmoteStatusendring(connection, dialogmoteStatusendring)
                connection.commit()
            }

            val oppgaver = database.connection.getPersonOppgaveList(UserConstants.ARBEIDSTAKER_FNR)
            oppgaver.size shouldBeEqualTo 0
        }

        it("Add a personoppgavehendelse after a personoppgave has been closed") {
        }
    }
})
