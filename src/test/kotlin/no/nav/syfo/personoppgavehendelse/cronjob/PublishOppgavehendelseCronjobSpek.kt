package no.nav.syfo.personoppgavehendelse.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.updatePersonoppgaveSetBehandlet
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.PublishPersonoppgavehendelseService
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.generators.generatePersonoppgave
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Future

class PublishOppgavehendelseCronjobSpek : Spek({
    describe(PublishOppgavehendelseCronjob::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaProducer = mockk<KafkaProducer<String, KPersonoppgavehendelse>>()

            val personoppgaveHendelseProducer = PersonoppgavehendelseProducer(
                producer = kafkaProducer,
            )
            val publishOppgavehendelseService = PublishPersonoppgavehendelseService(
                database = database,
                personoppgavehendelseProducer = personoppgaveHendelseProducer,
                personOppgaveRepository = PersonOppgaveRepository(database = database),
            )

            val publishOppgavehendelseCronjob = PublishOppgavehendelseCronjob(
                publishOppgavehendelseService = publishOppgavehendelseService,
            )

            beforeEachTest {
                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
                database.dropData()
            }

            val unpublishedUbehandletPersonoppgave = generatePersonoppgave(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
                type = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                publish = true,
            )

            fun createPersonoppgaver(vararg oppgaver: PersonOppgave) {
                database.connection.use {
                    oppgaver.forEach { oppgave ->
                        it.createPersonOppgave(oppgave)
                    }
                    it.commit()
                }
            }

            it("Will publish unpublished personoppgaver") {
                createPersonoppgaver(
                    unpublishedUbehandletPersonoppgave,
                    unpublishedUbehandletPersonoppgave.copy(
                        uuid = UUID.randomUUID(),
                        referanseUuid = UUID.randomUUID(),
                        type = PersonOppgaveType.BEHANDLERDIALOG_SVAR,
                    ),
                )

                runBlocking {
                    val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 2
                }

                val kafkaRecordSlot1 = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                val kafkaRecordSlot2 = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                verifyOrder {
                    kafkaProducer.send(capture(kafkaRecordSlot1))
                    kafkaProducer.send(capture(kafkaRecordSlot2))
                }

                val kafkaPersonoppgavehendelse1 = kafkaRecordSlot1.captured.value()
                kafkaPersonoppgavehendelse1.personident shouldBeEqualTo unpublishedUbehandletPersonoppgave.personIdent.value
                kafkaPersonoppgavehendelse1.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.AKTIVITETSKRAV_VURDER_STANS_MOTTATT.name

                val kafkaPersonoppgavehendelse2 = kafkaRecordSlot2.captured.value()
                kafkaPersonoppgavehendelse2.personident shouldBeEqualTo unpublishedUbehandletPersonoppgave.personIdent.value
                kafkaPersonoppgavehendelse2.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT.name
            }

            it("Will only publish newest unpublished personoppgave of same type") {
                val unpublishedBehandletPersonoppgave = unpublishedUbehandletPersonoppgave.copy(
                    uuid = UUID.randomUUID(),
                    referanseUuid = UUID.randomUUID(),
                    sistEndret = LocalDateTime.now().plusMinutes(1),
                    behandletTidspunkt = LocalDateTime.now(),
                    behandletVeilederIdent = UserConstants.VEILEDER_IDENT,
                )
                database.connection.use {
                    it.createPersonOppgave(unpublishedUbehandletPersonoppgave)
                    it.createPersonOppgave(unpublishedBehandletPersonoppgave)
                    it.updatePersonoppgaveSetBehandlet(unpublishedBehandletPersonoppgave)
                    it.commit()
                }

                runBlocking {
                    val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 2 // Her burde det bli 1, men siden publish()-metoden egentlig bare er en "maybe-publish" så teller den updated++ her uansett om det faktisk ble publisert
                }

                val kafkaRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(kafkaRecordSlot))
                }

                val kafkaPersonoppgavehendelse = kafkaRecordSlot.captured.value()
                kafkaPersonoppgavehendelse.personident shouldBeEqualTo unpublishedUbehandletPersonoppgave.personIdent.value
                kafkaPersonoppgavehendelse.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.AKTIVITETSKRAV_VURDER_STANS_BEHANDLET.name
            }

            it("Will not publish already published oppgave") {
                val alreadyPublishedOppgave = unpublishedUbehandletPersonoppgave.copy(
                    publishedAt = OffsetDateTime.now(),
                    publish = false,
                )
                createPersonoppgaver(alreadyPublishedOppgave)

                runBlocking {
                    val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }
            }

            it("Will not publish if no personoppgaver") {
                runBlocking {
                    val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }
            }

            it("Will fail when publishing to topic throws error") {
                createPersonoppgaver(unpublishedUbehandletPersonoppgave)
                coEvery {
                    kafkaProducer.send(any())
                } coAnswers { throw Exception() }

                runBlocking {
                    val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()

                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 1) {
                    kafkaProducer.send(any())
                }
            }
        }
    }
})
