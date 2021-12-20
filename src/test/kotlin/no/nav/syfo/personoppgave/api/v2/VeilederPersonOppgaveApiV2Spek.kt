package no.nav.syfo.personoppgave.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.kafka.kafkaConsumerConfig
import no.nav.syfo.kafka.kafkaProducerConfig
import no.nav.syfo.oversikthendelse.OVERSIKTHENDELSE_TOPIC
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.personoppgave.api.PersonOppgaveVeileder
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VEILEDER_IDENT
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.*

class VeilederPersonOppgaveApiV2Spek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    describe(VeilederPersonOppgaveApiV2Spek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val env = externalMockEnvironment.environment

            val baseUrl = registerVeilederPersonOppgaveApiV2BasePath

            fun Properties.overrideForTest(): Properties = apply {
                remove("security.protocol")
                remove("sasl.mechanism")
            }

            val consumerPropertiesOversikthendelse = kafkaConsumerConfig(env = env)
                .overrideForTest()
                .apply {
                    put("specific.avro.reader", false)
                    put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
                    put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
                }
            val consumerOversikthendelse = KafkaConsumer<String, String>(consumerPropertiesOversikthendelse)
            consumerOversikthendelse.subscribe(listOf(OVERSIKTHENDELSE_TOPIC))

            val producerProperties = kafkaProducerConfig(env = env)
                .overrideForTest()
            val oversikthendelseRecordProducer = KafkaProducer<String, KOversikthendelse>(producerProperties)
            val oversikthendelseProducer = OversikthendelseProducer(oversikthendelseRecordProducer)

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                oversikthendelseProducer = oversikthendelseProducer,
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

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azureAppClientId,
                issuer = externalMockEnvironment.wellKnownInternADV2Mock.issuer,
                navIdent = VEILEDER_IDENT,
            )

            describe("Get PersonOppgave for PersonIdent") {
                val url = "$baseUrl/personident"

                it("should return status BadRequest if not NAV_PERSONIDENT_HEADER is supplied") {

                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid Fodselsnummer is supplied") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("should return status Forbidden Veileder does not have access to request PersonIdent") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1).plus("0"))
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }

                it("should return status NoContent if there is no PersonOppgaver for PersonIdent") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.NoContent
                    }
                }

                it("should return PersonOppgaveList if there is a PersonOppgave for PersonIdent with type OppfolgingsplanLPS") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV
                    val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
                    database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV,
                        personOppgaveType
                    )

                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val personOppgaveList = objectMapper.readValue<List<PersonOppgaveVeileder>>(response.content!!)

                        personOppgaveList.size shouldBeEqualTo 1

                        val personOppgave = personOppgaveList.first()
                        personOppgave.uuid.shouldNotBeNull()
                        personOppgave.referanseUuid shouldBeEqualTo kOppfolgingsplanLPSNAV.getUuid()
                        personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                        personOppgave.virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                        personOppgave.type shouldBeEqualTo personOppgaveType.name
                        personOppgave.behandletTidspunkt.shouldBeNull()
                        personOppgave.behandletVeilederIdent.shouldBeNull()
                        personOppgave.opprettet.shouldNotBeNull()
                    }
                }
            }

            describe("Process PersonOppgave for PersonIdent") {
                it("should return OK and not send Oversikthendelse if processed 1 of 2 existing PersonOppgave") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV
                    val kOppfolgingsplanLPSNAV2 = generateKOppfolgingsplanLPSNAV2
                    val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS

                    val uuid = database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV,
                        personOppgaveType
                    ).second

                    database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV2,
                        personOppgaveType
                    ).second

                    val urlProcess = "$baseUrl/$uuid/behandle"
                    val urlGet = "$baseUrl/personident"

                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlGet) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val personOppgaveList = objectMapper.readValue<List<PersonOppgaveVeileder>>(response.content!!)

                        personOppgaveList.size shouldBeEqualTo 2

                        val personOppgaveBehandletList = personOppgaveList.filter {
                            it.behandletTidspunkt != null
                        }
                        personOppgaveBehandletList.size shouldBeEqualTo 1
                        val personOppgaveBehandlet = personOppgaveBehandletList.first()
                        personOppgaveBehandlet.uuid.shouldNotBeNull()
                        personOppgaveBehandlet.referanseUuid shouldBeEqualTo kOppfolgingsplanLPSNAV.getUuid()
                        personOppgaveBehandlet.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                        personOppgaveBehandlet.virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                        personOppgaveBehandlet.type shouldBeEqualTo personOppgaveType.name
                        personOppgaveBehandlet.behandletTidspunkt.shouldNotBeNull()
                        personOppgaveBehandlet.behandletVeilederIdent.shouldNotBeNull()
                        personOppgaveBehandlet.opprettet.shouldNotBeNull()

                        val personOppgaveUbehandletList = personOppgaveList.filter {
                            it.behandletTidspunkt == null
                        }
                        personOppgaveUbehandletList.size shouldBeEqualTo 1
                        val personOppgaveUbehandlet = personOppgaveUbehandletList.first()
                        personOppgaveUbehandlet.uuid.shouldNotBeNull()
                        personOppgaveUbehandlet.referanseUuid shouldBeEqualTo kOppfolgingsplanLPSNAV2.getUuid()
                        personOppgaveUbehandlet.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV2.getFodselsnummer()
                        personOppgaveUbehandlet.virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV2.getVirksomhetsnummer()
                        personOppgaveUbehandlet.type shouldBeEqualTo personOppgaveType.name
                        personOppgaveUbehandlet.behandletTidspunkt.shouldBeNull()
                        personOppgaveUbehandlet.behandletVeilederIdent.shouldBeNull()
                        personOppgaveUbehandlet.opprettet.shouldNotBeNull()

                        val messages: ArrayList<KOversikthendelse> = arrayListOf()
                        consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                            val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                            messages.add(consumedOversikthendelse)
                        }
                        messages.size shouldBeEqualTo 0
                    }
                }

                it("should return OK and send Oversikthendelse if processed 1 of  existing PersonOppgave") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV
                    val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS

                    val uuid = database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV,
                        personOppgaveType
                    ).second

                    val urlProcess = "$baseUrl/$uuid/behandle"
                    val urlGet = "$baseUrl/personident"

                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlGet) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val personOppgaveList = objectMapper.readValue<List<PersonOppgaveVeileder>>(response.content!!)

                        personOppgaveList.size shouldBeEqualTo 1

                        val personOppgave = personOppgaveList.first()
                        personOppgave.uuid.shouldNotBeNull()
                        personOppgave.referanseUuid shouldBeEqualTo kOppfolgingsplanLPSNAV.getUuid()
                        personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                        personOppgave.virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                        personOppgave.type shouldBeEqualTo personOppgaveType.name
                        personOppgave.behandletTidspunkt.shouldNotBeNull()
                        personOppgave.behandletVeilederIdent.shouldNotBeNull()
                        personOppgave.opprettet.shouldNotBeNull()
                    }

                    val messages: ArrayList<KOversikthendelse> = arrayListOf()
                    consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                        val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                        messages.add(consumedOversikthendelse)
                    }
                    messages.size shouldBeEqualTo 1
                    messages.first().fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                    messages.first().enhetId shouldBeEqualTo externalMockEnvironment.behandlendeEnhetMock.behandlendeEnhet.enhetId
                    messages.first().hendelseId shouldBeEqualTo OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET.name
                }
            }
        }
    }
})
