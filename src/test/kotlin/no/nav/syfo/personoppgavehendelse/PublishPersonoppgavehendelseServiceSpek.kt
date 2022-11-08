package no.nav.syfo.personoppgavehendelse

import io.mockk.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

class PublishPersonoppgavehendelseServiceSpek : Spek({
    val ONE_DAY_AGO = OffsetDateTime.now().minusDays(1)
    val TEN_DAYS_AGO = OffsetDateTime.now().minusDays(10)

    describe("Get correct unpublished oppgavehendelser") {
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

        describe("getUnpublishedOppgaver") {
            it("get oppgaver with publish true") {
                val pPersonoppgaver = generatePPersonoppgaver()
                every { connection.getPersonOppgaverByPublish(publish = true) } returns pPersonoppgaver

                val unpublished = publishPersonoppgavehendelseService.getUnpublishedOppgaver(connection)

                verify(exactly = 1) { connection.getPersonOppgaverByPublish(publish = true) }
                unpublished.size shouldBeEqualTo pPersonoppgaver.size
                unpublished[0].uuid shouldBeEqualTo pPersonoppgaver[0].uuid
            }
        }

        describe("publish") {
            it("don't publish oppgave if newer exists") {
                val moteUuid = UUID.randomUUID()
                val newerMoteUuid = UUID.randomUUID()
                val pPersonOppgave = generatePPersonoppgave(
                    moteuuid = moteUuid,
                    sistEndret = TEN_DAYS_AGO.toLocalDateTime(),
                )
                val personoppgave = generatePersonoppgave().copy(
                    uuid = pPersonOppgave.uuid,
                    sistEndret = pPersonOppgave.sistEndret,
                    referanseUuid = pPersonOppgave.referanseUuid,
                    publish = true,
                )
                val newerPOppgave = generatePPersonoppgave(
                    moteuuid = newerMoteUuid,
                    sistEndret = ONE_DAY_AGO.toLocalDateTime(),
                )
                every { connection.getPersonOppgaver(any()) } returns listOf(
                    pPersonOppgave,
                    newerPOppgave,
                )
                val updatedPersonoppgave = personoppgave.copy(publish = false)
                justRun { connection.updatePersonoppgave(any()) }

                publishPersonoppgavehendelseService.publish(connection, personoppgave,)

                verify(exactly = 0) { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any()) }
                verify(exactly = 1) { connection.getPersonOppgaver(personoppgave.personIdent) }
                verify(exactly = 1) { connection.updatePersonoppgave(updatedPersonoppgave) }
            }

            it("publish if newest") {
                val now = OffsetDateTime.now()
                val olderMoteUuid = UUID.randomUUID()
                val moteUuid = UUID.randomUUID()
                val olderPPersonOppgave = generatePPersonoppgave(
                    moteuuid = olderMoteUuid,
                    sistEndret = TEN_DAYS_AGO.toLocalDateTime(),
                )
                val pPersonOppgave = generatePPersonoppgave(
                    moteuuid = moteUuid,
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
                val updatedPersonOppgave = personOppgave.copy(
                    publish = false,
                    publishedAt = now,
                )
                every { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any()) } returns now
                justRun { connection.updatePersonoppgave(any()) }

                publishPersonoppgavehendelseService.publish(connection, personOppgave)

                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.DIALOGMOTESVAR_MOTTATT,
                        personOppgave = personOppgave,
                    )
                }
                verify(exactly = 1) { connection.getPersonOppgaver(personOppgave.personIdent) }
                verify(exactly = 1) { connection.updatePersonoppgave(updatedPersonOppgave) }
            }
        }
    }
})
