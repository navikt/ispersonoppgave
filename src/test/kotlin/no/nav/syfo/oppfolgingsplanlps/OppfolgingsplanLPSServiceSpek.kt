package no.nav.syfo.oppfolgingsplanlps

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.personoppgavehendelse.domain.*
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.generators.generateKOppfolgingsplanLPS
import no.nav.syfo.testutil.generators.generateKOppfolgingsplanLPSNoBehovforForBistand
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*
import java.util.concurrent.Future

class OppfolgingsplanLPSServiceSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe("OppfolgingsplanLPSService") {
        val externalMockEnvironment = ExternalMockEnvironment()

        val kafkaProducer = mockk<KafkaProducer<String, KPersonoppgavehendelse>>(relaxed = true)
        val personoppgavehendelseProducer = PersonoppgavehendelseProducer(kafkaProducer)

        with(TestApplicationEngine()) {
            start()

            val database = externalMockEnvironment.database

            val oppfolgingsplanLPSService = OppfolgingsplanLPSService(
                database,
                personoppgavehendelseProducer,
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
            )

            beforeEachTest {
                clearMocks(kafkaProducer)
                coEvery { kafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }

            afterEachTest {
                database.dropData()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            describe("Receive kOppfolgingsplanLPS") {
                it("should create a new PPersonOppgave with correct type when behovForBistand=true") {
                    val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS

                    runBlocking {
                        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPS)
                    }

                    val personListe = database.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                    personListe.size shouldBe 1
                    personListe[0].fnr shouldBeEqualTo kOppfolgingsplanLPS.fodselsnummer
                    personListe[0].virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPS.virksomhetsnummer
                    personListe[0].type shouldBeEqualTo PersonOppgaveType.OPPFOLGINGSPLANLPS.name
                    personListe[0].referanseUuid shouldBeEqualTo UUID.fromString(kOppfolgingsplanLPS.uuid)
                    personListe[0].oversikthendelseTidspunkt.shouldNotBeNull()

                    val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }

                    val producedPersonoppgaveHendelse = producerRecordSlot.captured.value()

                    producedPersonoppgaveHendelse.personident shouldBeEqualTo kOppfolgingsplanLPS.fodselsnummer
                    producedPersonoppgaveHendelse.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name
                }

                it("should not create a new PPersonOppgave with correct type when behovForBistand=false") {
                    val kOppfolgingsplanLPS = generateKOppfolgingsplanLPSNoBehovforForBistand

                    runBlocking {
                        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPS)
                    }

                    val personListe = database.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                    personListe.size shouldBe 0

                    verify(exactly = 0) { kafkaProducer.send(any()) }
                }
            }
        }
    }
})
