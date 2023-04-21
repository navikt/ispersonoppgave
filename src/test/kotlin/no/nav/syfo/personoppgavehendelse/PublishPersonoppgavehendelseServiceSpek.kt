package no.nav.syfo.personoppgavehendelse

import io.mockk.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

class PublishPersonoppgavehendelseServiceSpek : Spek({
    val ONE_DAY_AGO = OffsetDateTime.now().minusDays(1)
    val TEN_DAYS_AGO = OffsetDateTime.now().minusDays(10)

    describe("Get and publish correct unpublished oppgavehendelser") {
        val connection = mockk<Connection>(relaxed = true)
        val personoppgavehendelseProducer = mockk<PersonoppgavehendelseProducer>()
        val publishPersonoppgavehendelseService = PublishPersonoppgavehendelseService(
            personoppgavehendelseProducer = personoppgavehendelseProducer,
        )
        beforeEachTest {
            mockkStatic("no.nav.syfo.personoppgave.GetPersonOppgaveQueriesKt")
            mockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
        }

        afterEachTest {
            clearMocks(connection, personoppgavehendelseProducer)
            unmockkStatic("no.nav.syfo.personoppgave.GetPersonOppgaveQueriesKt")
            unmockkStatic("no.nav.syfo.personoppgave.PersonOppgaveQueriesKt")
        }

        describe("get unpublished oppgavehendelser") {
            it("get oppgaver with publish true") {
                val pPersonoppgaver = generatePPersonoppgaver()
                every { connection.getPersonOppgaverByPublish(publish = true) } returns pPersonoppgaver

                val unpublished = publishPersonoppgavehendelseService.getUnpublishedOppgaver(connection)

                verify(exactly = 1) { connection.getPersonOppgaverByPublish(publish = true) }
                unpublished.size shouldBeEqualTo pPersonoppgaver.size
                unpublished[0].uuid shouldBeEqualTo pPersonoppgaver[0].uuid
            }
        }

        describe("publish oppgavehendelser") {
            it("doesn't publish an oppgave if newer exists") {
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
                every { connection.getPersonOppgaver(any()) } returns listOf(
                    pPersonOppgave,
                    newerPOppgave,
                )
                val updatedPersonoppgave = personoppgave.copy(publish = false)
                justRun { connection.updatePersonoppgave(any()) }

                publishPersonoppgavehendelseService.publish(connection, personoppgave,)

                verify(exactly = 0) { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
                verify(exactly = 1) { connection.getPersonOppgaver(personoppgave.personIdent) }
                verify(exactly = 1) { connection.updatePersonoppgave(updatedPersonoppgave) }
            }

            it("publishes an oppgavehendelse if the personoppgave is the newest") {
                val now = OffsetDateTime.now()
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
                every { connection.getPersonOppgaver(PersonIdent(pPersonOppgave.fnr)) } returns listOf(
                    olderPPersonOppgave,
                    pPersonOppgave,
                )
                justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
                justRun { connection.updatePersonoppgave(any()) }

                publishPersonoppgavehendelseService.publish(connection, personOppgave)

                verify(exactly = 1) { connection.getPersonOppgaver(personOppgave.personIdent) }
                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.DIALOGMOTESVAR_MOTTATT,
                        personIdent = personOppgave.personIdent,
                        personoppgaveId = personOppgave.uuid,
                    )
                }
                val updatedpersonoppgaveSlot = slot<PersonOppgave>()
                verify(exactly = 1) { connection.updatePersonoppgave(capture(updatedpersonoppgaveSlot)) }
                val updatedPersonoppgave = updatedpersonoppgaveSlot.captured
                updatedPersonoppgave.publishedAt shouldNotBeEqualTo null
                updatedPersonoppgave.publish shouldBeEqualTo false
                updatedPersonoppgave.referanseUuid shouldBeEqualTo moteUuid
                updatedPersonoppgave.uuid shouldBeEqualTo personOppgave.uuid
            }

            it("publishes an meldingFraBehandler oppgavehendelse") {
                val pPersonOppgave = generatePPersonoppgave().copy(
                    type = PersonOppgaveType.BEHANDLERDIALOG_SVAR.name,
                    publish = true,
                )
                val personOppgave = generatePersonoppgave().copy(
                    uuid = pPersonOppgave.uuid,
                    sistEndret = pPersonOppgave.sistEndret,
                    referanseUuid = pPersonOppgave.referanseUuid,
                    type = PersonOppgaveType.valueOf(pPersonOppgave.type),
                )
                every { connection.getPersonOppgaver(PersonIdent(pPersonOppgave.fnr)) } returns listOf(
                    pPersonOppgave,
                )
                justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
                justRun { connection.updatePersonoppgave(any()) }

                publishPersonoppgavehendelseService.publish(connection, personOppgave)

                verify(exactly = 1) { connection.getPersonOppgaver(personOppgave.personIdent) }
                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT,
                        personIdent = personOppgave.personIdent,
                        personoppgaveId = personOppgave.uuid,
                    )
                }
                val updatedpersonoppgaveSlot = slot<PersonOppgave>()
                verify(exactly = 1) { connection.updatePersonoppgave(capture(updatedpersonoppgaveSlot)) }
                val updatedPersonoppgave = updatedpersonoppgaveSlot.captured
                updatedPersonoppgave.publishedAt shouldNotBeEqualTo null
                updatedPersonoppgave.publish shouldBeEqualTo false
                updatedPersonoppgave.uuid shouldBeEqualTo personOppgave.uuid
            }
        }
    }
})
