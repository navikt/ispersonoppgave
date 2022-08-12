package no.nav.syfo.personoppgave.oppfolgingsplanlps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.personoppgavehendelse.domain.*
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.*

class OppfolgingsplanLPSServiceSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe("OppfolgingsplanLPSService") {
        val externalMockEnvironment = ExternalMockEnvironment()

        val env = externalMockEnvironment.environment

        val consumerPersonoppgavehendelse = testPersonoppgavehendelseConsumer(
            environment = externalMockEnvironment.environment,
        )

        val personoppgavehendelseProducer = testPersonoppgavehendelseProducer(
            environment = externalMockEnvironment.environment,
        )

        with(TestApplicationEngine()) {
            start()

            val database = externalMockEnvironment.database

            val azureAdClient = AzureAdV2Client(
                azureAppClientId = env.azureAppClientId,
                azureAppClientSecret = env.azureAppClientSecret,
                azureTokenEndpoint = externalMockEnvironment.azureAdV2Mock.url,
            )

            val oppfolgingsplanLPSService = OppfolgingsplanLPSService(
                database,
                personoppgavehendelseProducer,
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
            )

            afterEachTest {
                database.connection.dropData()
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            describe("Receive kOppfolgingsplanLPSNAV") {
                it("should create a new PPersonOppgave with correct type when behovForBistand=true") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV

                    runBlocking {
                        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPSNAV)
                    }

                    val personListe = database.connection.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                    personListe.size shouldBe 1
                    personListe[0].fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                    personListe[0].virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                    personListe[0].type shouldBeEqualTo PersonOppgaveType.OPPFOLGINGSPLANLPS.name
                    personListe[0].referanseUuid shouldBeEqualTo UUID.fromString(kOppfolgingsplanLPSNAV.getUuid())
                    personListe[0].oversikthendelseTidspunkt.shouldNotBeNull()

                    val messages: ArrayList<KPersonoppgavehendelse> = arrayListOf()
                    consumerPersonoppgavehendelse.poll(Duration.ofMillis(5000)).forEach {
                        val consumedPersonoppgavehendelse: KPersonoppgavehendelse = objectMapper.readValue(it.value())
                        messages.add(consumedPersonoppgavehendelse)
                    }

                    messages.size shouldBeEqualTo 1
                    messages.first().personident shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                    messages.first().hendelsetype shouldBeEqualTo PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name
                }

                it("should not create a new PPersonOppgave with correct type when behovForBistand=false") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAVNoBehovforForBistand

                    runBlocking {
                        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPSNAV)
                    }

                    val personListe = database.connection.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                    personListe.size shouldBe 0

                    val messages: ArrayList<KPersonoppgavehendelse> = arrayListOf()
                    consumerPersonoppgavehendelse.poll(Duration.ofMillis(5000)).forEach {
                        val consumedPersonoppgavehendelse: KPersonoppgavehendelse = objectMapper.readValue(it.value())
                        messages.add(consumedPersonoppgavehendelse)
                    }
                    messages.size shouldBeEqualTo 0
                }
            }
        }
    }
})
