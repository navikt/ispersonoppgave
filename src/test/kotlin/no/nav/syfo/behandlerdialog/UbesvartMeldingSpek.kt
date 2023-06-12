package no.nav.syfo.behandlerdialog

import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.personoppgave.getPersonOppgaveList
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.mock.mockReceiveMeldingDTO
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class UbesvartMeldingSpek : Spek({
    describe("Handle ubesvart-melding topic") {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaMeldingConsumer = mockk<KafkaConsumer<String, KMeldingDTO>>()
            val kafkaUbesvartMelding = KafkaUbesvartMelding(database)

            beforeEachTest {
                every { kafkaMeldingConsumer.commitSync() } returns Unit
            }

            afterEachTest {
                database.connection.dropData()
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            it("stores ubesvart melding from kafka as oppgave in database") {
                val referanseUuid = UUID.randomUUID()
                val kMeldingDTO = generateKMeldingDTO(referanseUuid)
                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingDTO,
                    kafkaConsumer = kafkaMeldingConsumer,
                )

                kafkaUbesvartMelding.pollAndProcessRecords(
                    kafkaConsumer = kafkaMeldingConsumer,
                )

                val pPersonOppgave = database.connection.getPersonOppgaveByReferanseUuid(
                    referanseUuid = referanseUuid,
                )
                pPersonOppgave?.publish shouldBeEqualTo true
                pPersonOppgave?.type shouldBeEqualTo PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART.name
            }

            it("creates no new ubesvart oppgave if already received svar for the same melding") {
                val kafkaMeldingFraBehandler = KafkaMeldingFraBehandler(database)
                val referanseUuid = UUID.randomUUID()
                val kMeldingFraBehandlerDTO = generateKMeldingDTO(referanseUuid)
                val kUbesvartMeldingDTO = generateKMeldingDTO(referanseUuid)

                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingFraBehandlerDTO,
                    kafkaConsumer = kafkaMeldingConsumer,
                )
                kafkaMeldingFraBehandler.pollAndProcessRecords(kafkaMeldingConsumer)
                mockReceiveMeldingDTO(
                    kMeldingDTO = kUbesvartMeldingDTO,
                    kafkaConsumer = kafkaMeldingConsumer,
                )
                kafkaUbesvartMelding.pollAndProcessRecords(kafkaMeldingConsumer)

                val personoppgaveList = database.getPersonOppgaveList(
                    personIdent = PersonIdent(kUbesvartMeldingDTO.personIdent),
                ).map { it.toPersonOppgave() }
                personoppgaveList.size shouldBeEqualTo 1
                val personoppgaveMeldingFraBehandler = personoppgaveList.first()
                personoppgaveMeldingFraBehandler.type shouldBeEqualTo PersonOppgaveType.BEHANDLERDIALOG_SVAR
                personoppgaveMeldingFraBehandler.referanseUuid shouldBeEqualTo referanseUuid
            }
        }
    }
})
