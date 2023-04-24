package no.nav.syfo.meldingfrabehandler

import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.meldingfrabehandler.domain.KMeldingFraBehandler
import no.nav.syfo.meldingfrabehandler.kafka.pollAndProcessMeldingFraBehandler
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.testutil.*
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.*
import java.util.*

class MeldingFraBehandlerSpek : Spek({
    describe("Handle melding-fra-behandler topic") {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaMeldingFraBehandlerConsumer = mockk<KafkaConsumer<String, KMeldingFraBehandler>>()

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
                val kMeldingFraBehandler = generateKMeldingFraBehandler(referanseUuid)
                mockReceiveMeldingFraBehandler(
                    kMeldingFraBehandler = kMeldingFraBehandler,
                    mockKafkaMeldingFraBehandler = kafkaMeldingFraBehandlerConsumer,
                )

                pollAndProcessMeldingFraBehandler(
                    database = database,
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

fun mockReceiveMeldingFraBehandler(
    kMeldingFraBehandler: KMeldingFraBehandler,
    mockKafkaMeldingFraBehandler: KafkaConsumer<String, KMeldingFraBehandler>,
) {
    every { mockKafkaMeldingFraBehandler.poll(any<Duration>()) } returns ConsumerRecords(
        mapOf(
            meldingFraBehandlerTopicPartition() to listOf(
                meldingFraBehandlerRecord(
                    kMeldingFraBehandler,
                ),
            )
        )
    )
}

fun meldingFraBehandlerTopicPartition() = TopicPartition(
    "topicnavn",
    0
)

fun meldingFraBehandlerRecord(
    kMeldingFraBehandler: KMeldingFraBehandler,
) = ConsumerRecord(
    "topicnavn",
    0,
    1,
    UUID.randomUUID().toString(),
    kMeldingFraBehandler
)
