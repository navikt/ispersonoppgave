package no.nav.syfo.aktivitetskrav

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.domain.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravExpiredVarselConsumer
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.time.LocalDate
import java.util.*

class AktivitetskravExpiredVarselSpek : Spek({
    describe("Handle aktivitetskrav-expired-varsel topic") {

        with(TestApplicationEngine()) {
            start()

            val topic = AktivitetskravExpiredVarselConsumer.AKTIVITETSKRAV_EXPIRED_VARSEL_TOPIC
            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaConsumer = mockk<KafkaConsumer<String, ExpiredVarsel>>()
            val personoppgavehendelseProducer = mockk<PersonoppgavehendelseProducer>()
            val aktivitetskravExpiredVarselConsumer = AktivitetskravExpiredVarselConsumer()

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
                justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
            }

            afterEachTest {
                database.connection.dropData()
                clearMocks(personoppgavehendelseProducer)
            }

            it("Consumes forhandsvarsel frist expired") {
                val expiredVarsel = ExpiredVarsel(
                    uuid = UUID.randomUUID(),
                    personIdent = ARBEIDSTAKER_FNR.value,
                    svarfrist = LocalDate.now(),
                )
                every { kafkaConsumer.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        TopicPartition(topic, 0) to listOf(
                            ConsumerRecord(
                                topic,
                                0,
                                1,
                                UUID.randomUUID().toString(),
                                expiredVarsel,
                            ),
                        )
                    )
                )
                aktivitetskravExpiredVarselConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }
            }
        }
    }
})
