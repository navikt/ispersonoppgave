package no.nav.syfo.sykmelding

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.behandler.kafka.sykmelding.KafkaSykmeldingConsumer
import no.nav.syfo.behandler.kafka.sykmelding.SYKMELDING_TOPIC
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKafkaSykmelding
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class SykmeldingConsumerSpek : Spek({
    describe("SykmeldingConsumer") {

        with(TestApplicationEngine()) {
            start()

            val topic = SYKMELDING_TOPIC
            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaConsumer = mockk<KafkaConsumer<String, ReceivedSykmeldingDTO>>()
            val kafkaSykmeldingConsumer = KafkaSykmeldingConsumer(database = database)

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
            }

            afterEachTest {
                database.dropData()
                clearMocks(kafkaConsumer)
            }

            describe("Consume sykmelding") {
                val sykmelding = generateKafkaSykmelding(
                    uuid = UUID.randomUUID()
                )
                it("Consumes sykmelding and creates oppgave") {
                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = sykmelding,
                        topic = topic,
                    )

                    kafkaSykmeldingConsumer.pollAndProcessRecords(
                        kafkaConsumer = kafkaConsumer,
                    )
                    verify(exactly = 1) {
                        kafkaConsumer.commitSync()
                    }

                    val personOppgave = database.getPersonOppgaver(
                        personIdent = PersonIdent(sykmelding.personNrPasient),
                    ).map { it.toPersonOppgave() }.first()
                    personOppgave.personIdent.value shouldBeEqualTo sykmelding.personNrPasient
                    personOppgave.publish shouldBeEqualTo true
                    personOppgave.type shouldBeEqualTo PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
                    personOppgave.behandletTidspunkt shouldBe null
                }
            }
        }
    }
})
