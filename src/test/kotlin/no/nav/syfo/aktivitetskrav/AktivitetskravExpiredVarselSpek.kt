package no.nav.syfo.aktivitetskrav

import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.aktivitetskrav.domain.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.domain.VarselType
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravExpiredVarselConsumer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
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
            val vurderStansService = VurderStansService(
                database = database,
            )
            val aktivitetskravExpiredVarselConsumer = AktivitetskravExpiredVarselConsumer(
                vurderStansService = vurderStansService,
            )

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
            }

            afterEachTest {
                database.connection.dropData()
            }

            it("Consumes expired varsel") {
                val expiredVarsel = ExpiredVarsel(
                    varselUuid = UUID.randomUUID(),
                    createdAt = LocalDate.now().atStartOfDay(),
                    personIdent = PersonIdent(ARBEIDSTAKER_FNR.value),
                    varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
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

                val personOppgave = database.connection.getPersonOppgaverByReferanseUuid(
                    referanseUuid = expiredVarsel.varselUuid,
                ).map { it.toPersonOppgave() }.first()
                personOppgave.publish shouldBeEqualTo true
                personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                personOppgave.personIdent shouldBeEqualTo ARBEIDSTAKER_FNR
            }
        }
    }
})
