package no.nav.syfo.personoppgave.oppfolgingsplanlps

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.util.InternalAPI
import io.mockk.every
import io.mockk.mockk
import no.nav.common.KafkaEnvironment
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.kafka.kafkaConsumerConfig
import no.nav.syfo.kafka.kafkaProducerConfig
import no.nav.syfo.oversikthendelse.OVERSIKTHENDELSE_TOPIC
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.generator.generateBehandlendeEnhet
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

    val env = testEnvironment(getRandomPort(), embeddedEnvironment.brokersURL)
    val credentials = vaultSecrets

    val consumerPropertiesOversikthendelse = kafkaConsumerConfig(env, credentials)
        .overrideForTest()
        .apply {
            put("specific.avro.reader", false)
            put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        }
    val consumerOversikthendelse = KafkaConsumer<String, String>(consumerPropertiesOversikthendelse)
    consumerOversikthendelse.subscribe(listOf(OVERSIKTHENDELSE_TOPIC))

    describe("OppfolgingsplanLPSService") {
        val database by lazy { TestDB() }

        val responseBehandlendeEnhet = generateBehandlendeEnhet
        val behandlendeEnhetClient = mockk<BehandlendeEnhetClient>()

        val producerProperties = kafkaProducerConfig(env, vaultSecrets)
            .overrideForTest()
        val oversikthendelseRecordProducer = KafkaProducer<String, KOversikthendelse>(producerProperties)
        val oversikthendelseProducer = OversikthendelseProducer(oversikthendelseRecordProducer)

        val oppfolgingsplanLPSService = OppfolgingsplanLPSService(
            database,
            behandlendeEnhetClient,
            oversikthendelseProducer
        )

        beforeGroup {
            embeddedEnvironment.start()
        }

        afterGroup {
            embeddedEnvironment.tearDown()
            database.stop()
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
                every {
                    behandlendeEnhetClient.getEnhet(ARBEIDSTAKER_FNR, "")
                } returns responseBehandlendeEnhet
            }

            afterEachTest {
                database.connection.dropData()
            }

            describe("Receive kOppfolgingsplanLPSNAV") {
                it("should create a new PPersonOppgave with correct type when behovForBistand=true") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV

                    oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPSNAV)

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
                    messages.first().enhetId shouldBeEqualTo responseBehandlendeEnhet.enhetId
                    messages.first().hendelseId shouldBeEqualTo OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name
                }

                it("should not create a new PPersonOppgave with correct type when behovForBistand=false") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAVNoBehovforForBistand

                    oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPSNAV)

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
