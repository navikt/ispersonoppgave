package no.nav.syfo.behandlerdialog

import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.mock.mockReceiveMeldingDTO
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class MeldingFraBehandlerSpek : Spek({
    describe("Handle melding-fra-behandler topic") {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaMeldingFraBehandlerConsumer = mockk<KafkaConsumer<String, KMeldingDTO>>()
            val kafkaMeldingFraBehandler = KafkaMeldingFraBehandler(database = database)

            beforeEachTest {
                every { kafkaMeldingFraBehandlerConsumer.commitSync() } returns Unit
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

            it("stores melding fra behandler from kafka in database") {
                val referanseUuid = UUID.randomUUID()
                val kMeldingFraBehandler = generateKMeldingDTO(referanseUuid)
                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingFraBehandler,
                    kafkaConsumer = kafkaMeldingFraBehandlerConsumer,
                )

                kafkaMeldingFraBehandler.pollAndProcessRecords(
                    kafkaConsumer = kafkaMeldingFraBehandlerConsumer,
                )

                val pPersonOppgave = database.connection.getPersonOppgaveByReferanseUuid(
                    referanseUuid = referanseUuid,
                )
                pPersonOppgave?.publish shouldBeEqualTo true
                pPersonOppgave?.type shouldBeEqualTo PersonOppgaveType.BEHANDLERDIALOG_SVAR.name
            }
        }
    }
})
