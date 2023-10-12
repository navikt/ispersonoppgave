package no.nav.syfo.aktivitetskrav

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.aktivitetskrav.domain.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravExpiredVarselConsumer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import no.nav.syfo.personoppgave.getUbehandledePersonOppgaver
import no.nav.syfo.personoppgave.updatePersonoppgaveSetBehandlet
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateExpiredVarsel
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

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
                database.dropData()
                clearMocks(kafkaConsumer)
            }

            val expiredVarsel = generateExpiredVarsel()

            it("Consumes expired varsel") {
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = expiredVarsel,
                    topic = topic,
                )
                aktivitetskravExpiredVarselConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgave = database.connection.use { connection ->
                    connection.getPersonOppgaverByReferanseUuid(
                        referanseUuid = expiredVarsel.varselUuid,
                    ).map { it.toPersonOppgave() }.first()
                }
                personOppgave.publish shouldBeEqualTo true
                personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                personOppgave.personIdent shouldBeEqualTo ARBEIDSTAKER_FNR
            }
            it("Consumes expired varsel creates personoppgave but not duplicate") {
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = expiredVarsel,
                    recordValue2 = expiredVarsel.copy(),
                    topic = topic,
                )
                aktivitetskravExpiredVarselConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgaver = database.connection.use { connection ->
                    connection.getPersonOppgaverByReferanseUuid(
                        referanseUuid = expiredVarsel.varselUuid,
                    ).map { it.toPersonOppgave() }
                }
                personOppgaver.size shouldBeEqualTo 1
                val personOppgave = personOppgaver.first()
                personOppgave.publish shouldBeEqualTo true
                personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                personOppgave.personIdent shouldBeEqualTo ARBEIDSTAKER_FNR
            }
            it("Consumes expired varsel creates personoppgave if existing is behandlet") {
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = expiredVarsel,
                    topic = topic,
                )
                aktivitetskravExpiredVarselConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                database.connection.use {
                    val personOppgave = it.getPersonOppgaverByReferanseUuid(
                        referanseUuid = expiredVarsel.varselUuid,
                    ).map { it.toPersonOppgave() }.first()
                    it.updatePersonoppgaveSetBehandlet(personOppgave)
                    it.commit()
                }
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = expiredVarsel.copy(),
                    topic = topic,
                )
                aktivitetskravExpiredVarselConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val personOppgaver = database.connection.use { connection ->
                    connection.getUbehandledePersonOppgaver(
                        personIdent = PersonIdent(ARBEIDSTAKER_FNR.value),
                        personOppgaveType = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                    ).map { it.toPersonOppgave() }
                }
                personOppgaver.size shouldBeEqualTo 1
                val personOppgave = personOppgaver.first()
                personOppgave.publish shouldBeEqualTo true
                personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                personOppgave.personIdent shouldBeEqualTo ARBEIDSTAKER_FNR
            }
        }
    }
})
