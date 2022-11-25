package no.nav.syfo.dialogmotestatusendring

import io.mockk.*
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.personoppgave.*
import no.nav.syfo.testutil.*
import no.nav.syfo.util.toLocalDateTimeOslo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

object DialogmoteStatusendringServiceSpek : Spek({
    val ONE_DAY_AGO = OffsetDateTime.now().minusDays(1)
    val TEN_DAYS_AGO = OffsetDateTime.now().minusDays(10)
    val HAPPENS_NOW = OffsetDateTime.now()
    val PERSONOPPGAVE_QUERIES_PATH = "no.nav.syfo.personoppgave.PersonOppgaveQueriesKt"

    describe("Finish personoppgave when receiving an endring in dialogmotestatus") {
        val dialogmoteUuid = UUID.randomUUID()
        val connection = mockk<Connection>(relaxed = true)

        beforeEachTest {
            mockkStatic(PERSONOPPGAVE_QUERIES_PATH)
        }

        afterEachTest {
            clearMocks(connection)
            unmockkStatic(PERSONOPPGAVE_QUERIES_PATH)
        }

        describe("Manage statusendring when an oppgave already exists on the dialogmøte") {

            it("Finish personoppgave when a dialogmote gets a referat") {
                val statusendring =
                    generateDialogmotestatusendring(
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
                every { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) } returns personoppgave
                justRun { connection.updatePersonoppgave(any()) }

                processDialogmoteStatusendring(connection, statusendring)

                verify(exactly = 1) { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) }
                verify(exactly = 1) { connection.updatePersonoppgave(updatePersonoppgave) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
            }

            it("Finish personoppgave when a dialogmote changes place or time") {
                val statusendring =
                    generateDialogmotestatusendring(
                        DialogmoteStatusendringType.NYTT_TID_STED,
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
                every { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) } returns personoppgave
                justRun { connection.updatePersonoppgave(any()) }

                processDialogmoteStatusendring(connection, statusendring)

                verify(exactly = 1) { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) }
                verify(exactly = 1) { connection.updatePersonoppgave(updatePersonoppgave) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
            }

            it("Finish personoppgave when a dialogmote is cancelled") {
                val statusendring =
                    generateDialogmotestatusendring(DialogmoteStatusendringType.AVLYST, dialogmoteUuid, HAPPENS_NOW)
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
                every { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) } returns personoppgave
                justRun { connection.updatePersonoppgave(any()) }

                processDialogmoteStatusendring(connection, statusendring)

                verify(exactly = 1) { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) }
                verify(exactly = 1) { connection.updatePersonoppgave(updatePersonoppgave) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
            }
        }

        describe("Manage statusendring when an oppgave doesn't exsist") {
            it("Create finished personoppgave when a dialogmote is created") {
                val statusendring =
                    generateDialogmotestatusendring(DialogmoteStatusendringType.INNKALT, dialogmoteUuid, HAPPENS_NOW)
                every { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) } returns null
                justRun { connection.createBehandletPersonoppgave(statusendring, any()) }

                processDialogmoteStatusendring(connection, statusendring)

                verify(exactly = 1) { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) }
                verify(exactly = 1) { connection.createBehandletPersonoppgave(statusendring, any()) }
                verify(exactly = 0) { connection.updatePersonoppgave(any()) }
            }
        }

        describe("Manage statusendring when they arrive out of order with møtesvar") {
            it("Do nothing if a dialogmøte created happened before personoppgave was sist endret") {
                val statusendring =
                    generateDialogmotestatusendring(DialogmoteStatusendringType.INNKALT, dialogmoteUuid, ONE_DAY_AGO)
                val personoppgave = generatePPersonoppgave(dialogmoteUuid, HAPPENS_NOW.toLocalDateTime())
                every { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) } returns personoppgave

                processDialogmoteStatusendring(connection, statusendring)

                verify(exactly = 1) { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 0) { connection.updatePersonoppgave(any()) }
            }

            it("Do nothing if a dialogmøte was moved status happened before personoppgave was sist endret") {
                val statusendring =
                    generateDialogmotestatusendring(
                        DialogmoteStatusendringType.NYTT_TID_STED,
                        dialogmoteUuid,
                        ONE_DAY_AGO
                    )
                val personoppgave = generatePPersonoppgave(dialogmoteUuid, HAPPENS_NOW.toLocalDateTime())
                every { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) } returns personoppgave

                processDialogmoteStatusendring(connection, statusendring)

                verify(exactly = 1) { connection.getPersonOppgaveByReferanseUuid(dialogmoteUuid) }
                verify(exactly = 0) { connection.createBehandletPersonoppgave(any(), any()) }
                verify(exactly = 0) { connection.updatePersonoppgave(any()) }
            }
        }
    }
})