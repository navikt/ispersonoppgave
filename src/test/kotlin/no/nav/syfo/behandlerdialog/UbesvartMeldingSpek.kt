package no.nav.syfo.behandlerdialog

import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
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
            val kafkaConsumer = mockk<KafkaConsumer<String, KMeldingDTO>>()
            val kafkaUbesvartMelding = KafkaUbesvartMelding(database)

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
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
                    kafkaConsumer = kafkaConsumer,
                )

                kafkaUbesvartMelding.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val pPersonOppgave = database.connection.getPersonOppgaveByReferanseUuid(
                    referanseUuid = referanseUuid,
                )
                pPersonOppgave?.publish shouldBeEqualTo true
                pPersonOppgave?.type shouldBeEqualTo PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART.name
            }
        }
    }
})
