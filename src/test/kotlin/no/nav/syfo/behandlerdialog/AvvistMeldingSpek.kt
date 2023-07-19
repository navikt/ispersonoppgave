package no.nav.syfo.behandlerdialog

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.AvvistMeldingConsumerService
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.personoppgave.getPersonOppgaveList
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.mock.mockReceiveMeldingDTO
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import java.util.concurrent.Future

class AvvistMeldingSpek : Spek({
    describe("Handle avvist-melding topic") {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaConsumer = mockk<KafkaConsumer<String, KMeldingDTO>>()
            val producer = mockk<KafkaProducer<String, KPersonoppgavehendelse>>()
            val personoppgavehendelseProducer = PersonoppgavehendelseProducer(producer)
            val personOppgaveService = PersonOppgaveService(
                database = database,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
            )
            val avvistMeldingService = AvvistMeldingService(database, personOppgaveService)
            val avvistMeldingConsumerService = AvvistMeldingConsumerService(avvistMeldingService)

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
                coEvery {
                    producer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }

            afterEachTest {
                database.connection.dropData()
                clearMocks(producer, kafkaConsumer)
            }

            it("stores avvist melding from kafka as oppgave in database and publish as new oppgave") {
                val referanseUuid = UUID.randomUUID()
                val kMeldingDTO = generateKMeldingDTO(referanseUuid)
                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingDTO,
                    kafkaConsumer = kafkaConsumer,
                )

                avvistMeldingConsumerService.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val personOppgave = database.connection.getPersonOppgaveByReferanseUuid(
                    referanseUuid = referanseUuid,
                )!!.toPersonOppgave()
                personOppgave.publish shouldBeEqualTo false
                personOppgave.type.name shouldBeEqualTo PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST.name

                val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                verify(exactly = 1) {
                    producer.send(capture(producerRecordSlot))
                }

                val kPersonoppgavehendelse = producerRecordSlot.captured.value()
                kPersonoppgavehendelse.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_MOTTATT.name
                kPersonoppgavehendelse.personident shouldBeEqualTo UserConstants.ARBEIDSTAKER_FNR.value
            }

            it("will not store avvist melding from kafka when value is null/tombstone") {
                mockReceiveMeldingDTO(
                    kMeldingDTO = null,
                    kafkaConsumer = kafkaConsumer,
                )
                avvistMeldingConsumerService.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val pPersonOppgaver = database.getPersonOppgaveList(
                    personIdent = UserConstants.ARBEIDSTAKER_FNR
                )
                pPersonOppgaver.size shouldBeEqualTo 0

                verify(exactly = 0) { producer.send(any()) }
            }
        }
    }
})
