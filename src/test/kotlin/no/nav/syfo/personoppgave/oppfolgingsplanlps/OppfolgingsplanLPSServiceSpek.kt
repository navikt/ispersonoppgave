package no.nav.syfo.personoppgave.oppfolgingsplanlps

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.common.KafkaEnvironment
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.kafka.*
import no.nav.syfo.oversikthendelse.OVERSIKTHENDELSE_TOPIC
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.oversikthendelse.retry.*
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_2_FNR
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.mock.*
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.*

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

@InternalAPI
object OppfolgingsplanLPSServiceSpek : Spek({

    val embeddedEnvironment = KafkaEnvironment(
        autoStart = false,
        withSchemaRegistry = false,
        topicNames = listOf(
            OVERSIKTHENDELSE_TOPIC
        )
    )

    val env = testEnvironment(embeddedEnvironment.brokersURL)

    val consumerPropertiesOversikthendelse = kafkaConsumerConfig(env = env)
        .overrideForTest()
        .apply {
            put("specific.avro.reader", false)
            put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        }
    val consumerOversikthendelse = KafkaConsumer<String, String>(consumerPropertiesOversikthendelse)
    consumerOversikthendelse.subscribe(listOf(OVERSIKTHENDELSE_TOPIC))

    val consumerPropertiesOversikthendelseRetry = kafkaConsumerOversikthendelseRetryProperties(env = env)
        .overrideForTest()
    val consumerOversikthendelseRetry = KafkaConsumer<String, String>(consumerPropertiesOversikthendelseRetry)
    consumerOversikthendelseRetry.subscribe(listOf(OVERSIKTHENDELSE_RETRY_TOPIC))

    describe("OppfolgingsplanLPSService") {
        val database by lazy { TestDB() }

        val azureAdMock = AzureAdV2Mock()
        val azureAdClient = AzureAdV2Client(
            azureAppClientId = env.azureAppClientId,
            azureAppClientSecret = env.azureAppClientSecret,
            azureTokenEndpoint = azureAdMock.url,
        )

        val behandlendeEnhetMock = BehandlendeEnhetMock()
        val behandlendeEnhetClient = BehandlendeEnhetClient(
            azureAdClient = azureAdClient,
            baseUrl = behandlendeEnhetMock.url,
            syfobehandlendeenhetClientId = env.syfobehandlendeenhetClientId,
        )

        val producerProperties = kafkaProducerConfig(env = env)
            .overrideForTest()
        val oversikthendelseRecordProducer = KafkaProducer<String, KOversikthendelse>(producerProperties)
        val oversikthendelseProducer = OversikthendelseProducer(oversikthendelseRecordProducer)

        val oversikthendelseRetryProducerProperties = kafkaProducerConfig(env = env)
            .overrideForTest()
        val oversikthendelseRetryRecordProducer = KafkaProducer<String, KOversikthendelseRetry>(oversikthendelseRetryProducerProperties)
        val oversikthendelseRetryProducer = OversikthendelseRetryProducer(oversikthendelseRetryRecordProducer)

        val oppfolgingsplanLPSService = OppfolgingsplanLPSService(
            database,
            behandlendeEnhetClient,
            oversikthendelseProducer,
            oversikthendelseRetryProducer
        )

        beforeGroup {
            embeddedEnvironment.start()

            azureAdMock.server.start()
            behandlendeEnhetMock.server.start()
        }

        afterGroup {
            embeddedEnvironment.tearDown()
            database.stop()

            azureAdMock.server.stop(1L, 10L)
            behandlendeEnhetMock.server.stop(1L, 10L)
        }

        with(TestApplicationEngine()) {
            start()

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            beforeEachTest {
                database.connection.dropData()
            }

            afterEachTest {
                database.connection.dropData()
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

                    val messages: ArrayList<KOversikthendelse> = arrayListOf()
                    consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                        val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                        messages.add(consumedOversikthendelse)
                    }

                    messages.size shouldBeEqualTo 1
                    messages.first().fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                    messages.first().enhetId shouldBeEqualTo behandlendeEnhetMock.behandlendeEnhet.enhetId
                    messages.first().hendelseId shouldBeEqualTo OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name
                }

                it("should create a new PPersonOppgave with correct type and send KOversikthendelseRetry when behovForBistand=true and behandlendeEnhet=null") {
                    val mockOversikthendelseRetryProducer = mockk<OversikthendelseRetryProducer>()
                    justRun { mockOversikthendelseRetryProducer.sendFirstOversikthendelseRetry(any(), any(), any(), any()) }

                    val oppfolgingsplanLPSServiceWithMockOversikthendelseRetryProcuer = OppfolgingsplanLPSService(
                        database,
                        behandlendeEnhetClient,
                        oversikthendelseProducer,
                        mockOversikthendelseRetryProducer
                    )

                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV(ARBEIDSTAKER_2_FNR)

                    val fodselsnummer = PersonIdentNumber(kOppfolgingsplanLPSNAV.getFodselsnummer())

                    runBlocking {
                        oppfolgingsplanLPSServiceWithMockOversikthendelseRetryProcuer.receiveOppfolgingsplanLPS(kOppfolgingsplanLPSNAV)
                    }

                    val personOppgaveListe = database.connection.getPersonOppgaveList(fodselsnummer)
                    personOppgaveListe.size shouldBe 1
                    val personOppgave = personOppgaveListe.first()
                    personOppgave.oversikthendelseTidspunkt.shouldBeNull()

                    val messagesOversikthendelse: ArrayList<KOversikthendelse> = arrayListOf()
                    consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                        val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                        messagesOversikthendelse.add(consumedOversikthendelse)
                    }
                    messagesOversikthendelse.size shouldBeEqualTo 0

                    verify(exactly = 1) {
                        mockOversikthendelseRetryProducer.sendFirstOversikthendelseRetry(
                            personIdentNumber = fodselsnummer,
                            oversikthendelseType = OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT,
                            personOppgaveId = personOppgave.id,
                            personOppgaveUUID = personOppgave.uuid
                        )
                    }
                }

                it("should not create a new PPersonOppgave with correct type when behovForBistand=false") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAVNoBehovforForBistand

                    runBlocking {
                        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPSNAV)
                    }

                    val personListe = database.connection.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                    personListe.size shouldBe 0

                    val messages: ArrayList<KOversikthendelse> = arrayListOf()
                    consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                        val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                        messages.add(consumedOversikthendelse)
                    }
                    messages.size shouldBeEqualTo 0
                }
            }
        }
    }
})
