package no.nav.syfo.oversikthendelse.retry

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.kafka.kafkaConsumerOversikthendelseRetryProperties
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_2_FNR
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.generator.generateKOversikthendelseRetry
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class KafkaOversikthendelseRetrySpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment()

        val oversikthendelseRetryTopic = OVERSIKTHENDELSE_RETRY_TOPIC

        val database = externalMockEnvironment.database
        val env = externalMockEnvironment.environment

        val azureAdClient = AzureAdV2Client(
            azureAppClientId = env.azureAppClientId,
            azureAppClientSecret = env.azureAppClientSecret,
            azureTokenEndpoint = externalMockEnvironment.azureAdV2Mock.url,
        )

        val behandlendeEnhetClient = BehandlendeEnhetClient(
            azureAdClient = azureAdClient,
            baseUrl = externalMockEnvironment.behandlendeEnhetMock.url,
            syfobehandlendeenhetClientId = env.syfobehandlendeenhetClientId,
        )

        val oversikthendelseProducer = testOversikthendelseProducer(
            environment = externalMockEnvironment.environment,
        )
        val consumerOversikthendelse = testOversikthendelseConsumer(
            environment = externalMockEnvironment.environment,
        )
        val oversikthendelseRetryProducer = testOversikthendelseRetryProducer(
            environment = externalMockEnvironment.environment,
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

        describe("Read and process KOversikthendelseRetry") {

            val oversikthendelseRetryService = OversikthendelseRetryService(
                behandlendeEnhetClient = behandlendeEnhetClient,
                database = database,
                oversikthendelseProducer = oversikthendelseProducer,
                oversikthendelseRetryProducer = oversikthendelseRetryProducer,
            )

            val consumerOversikthendelseRetry = testOversikthendelseRetryConsumer(
                environment = externalMockEnvironment.environment
            )

            val partition = 0
            val oversikthendelseRetryTopicPartition = TopicPartition(oversikthendelseRetryTopic, partition)

            it("should update PersonOppgave when KOversikthendelseRetry is ready to retry and retry is successful") {
                val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV
                val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
                val createdPersonOppgaveId = database.connection.createPersonOppgave(
                    kOppfolgingsplanLPSNAV,
                    personOppgaveType
                ).first

                val kOversikthendelseRetry = generateKOversikthendelseRetry.copy(
                    created = LocalDateTime.now().minusHours(RETRY_OVERSIKTHENDELSE_INTERVAL_MINUTES).minusMinutes(1),
                    retryTime = LocalDateTime.now().minusMinutes(1),
                    personOppgaveId = createdPersonOppgaveId
                )
                val kOversiktHendelseRetryJson = objectMapper.writeValueAsString(kOversikthendelseRetry)
                val oversiktHendelseRetryRecord = ConsumerRecord(
                    oversikthendelseRetryTopic,
                    partition,
                    1,
                    "something",
                    kOversiktHendelseRetryJson
                )

                val mockConsumerOversikthendelseRetry = mockk<KafkaConsumer<String, String>>()
                justRun { mockConsumerOversikthendelseRetry.commitSync() }
                every { mockConsumerOversikthendelseRetry.assignment() } returns emptySet()
                every { mockConsumerOversikthendelseRetry.poll(Duration.ofMillis(1000)) } returns ConsumerRecords(
                    mapOf(oversikthendelseRetryTopicPartition to listOf(oversiktHendelseRetryRecord))
                )

                runBlocking {
                    pollAndProcessOversikthendelseRetryTopic(
                        kafkaConsumer = mockConsumerOversikthendelseRetry,
                        oversikthendelseRetryService = oversikthendelseRetryService
                    )
                }

                val personOppgaveList = database.connection.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                personOppgaveList.size shouldBeEqualTo 1

                val personOppgave = personOppgaveList.first()

                personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                personOppgave.virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                personOppgave.type shouldBeEqualTo PersonOppgaveType.OPPFOLGINGSPLANLPS.name
                personOppgave.referanseUuid shouldBeEqualTo UUID.fromString(kOppfolgingsplanLPSNAV.getUuid())
                personOppgave.oversikthendelseTidspunkt.shouldNotBeNull()

                val messages: ArrayList<KOversikthendelse> = arrayListOf()
                consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                    val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                    messages.add(consumedOversikthendelse)
                }
                messages.size shouldBeEqualTo 1
                messages.first().fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                messages.first().enhetId shouldBeEqualTo externalMockEnvironment.behandlendeEnhetMock.behandlendeEnhet.enhetId
                messages.first().hendelseId shouldBeEqualTo OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name
            }

            it("should skip update of PersonOppgave when KOversikthendelseRetry has exceeded retry limit") {
                val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV
                val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
                val createdPersonOppgaveId = database.connection.createPersonOppgave(
                    kOppfolgingsplanLPSNAV,
                    personOppgaveType
                ).first

                val kOversikthendelseRetry = generateKOversikthendelseRetry.copy(
                    created = LocalDateTime.now().minusHours(RETRY_OVERSIKTHENDELSE_INTERVAL_MINUTES).minusMinutes(1),
                    retriedCount = RETRY_OVERSIKTHENDELSE_COUNT_LIMIT,
                    personOppgaveId = createdPersonOppgaveId
                )
                val kOversiktHendelseRetryJson = objectMapper.writeValueAsString(kOversikthendelseRetry)
                val oversiktHendelseRetryRecord = ConsumerRecord(
                    oversikthendelseRetryTopic,
                    partition,
                    1,
                    "something",
                    kOversiktHendelseRetryJson
                )

                val mockConsumerOversikthendelseRetry = mockk<KafkaConsumer<String, String>>()
                justRun { mockConsumerOversikthendelseRetry.commitSync() }
                every { mockConsumerOversikthendelseRetry.assignment() } returns emptySet()
                every { mockConsumerOversikthendelseRetry.poll(Duration.ofMillis(1000)) } returns ConsumerRecords(
                    mapOf(oversikthendelseRetryTopicPartition to listOf(oversiktHendelseRetryRecord))
                )

                runBlocking {
                    pollAndProcessOversikthendelseRetryTopic(
                        kafkaConsumer = mockConsumerOversikthendelseRetry,
                        oversikthendelseRetryService = oversikthendelseRetryService
                    )
                }

                val personOppgaveList = database.connection.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                personOppgaveList.size shouldBeEqualTo 1

                val personOppgave = personOppgaveList.first()

                personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                personOppgave.virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                personOppgave.type shouldBeEqualTo PersonOppgaveType.OPPFOLGINGSPLANLPS.name
                personOppgave.referanseUuid shouldBeEqualTo UUID.fromString(kOppfolgingsplanLPSNAV.getUuid())
                personOppgave.oversikthendelseTidspunkt.shouldBeNull()

                val messages: ArrayList<KOversikthendelse> = arrayListOf()
                consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                    val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                    messages.add(consumedOversikthendelse)
                }
                messages.size shouldBeEqualTo 0
            }
        }

        describe("Read and process KOversikthendelseRetry with unavailable BehandlendeEnhe") {
            val mockOversikthendelseRetryProducer = mockk<OversikthendelseRetryProducer>()
            justRun { mockOversikthendelseRetryProducer.sendAgainOversikthendelseRetry(any()) }
            justRun { mockOversikthendelseRetryProducer.sendRetriedOversikthendelseRetry(any()) }

            val oversikthendelseRetryService = OversikthendelseRetryService(
                behandlendeEnhetClient = behandlendeEnhetClient,
                database = database,
                oversikthendelseProducer = oversikthendelseProducer,
                oversikthendelseRetryProducer = mockOversikthendelseRetryProducer
            )

            val consumerPropertiesOversikthendelseRetry = kafkaConsumerOversikthendelseRetryProperties(env = env)
                .overrideForTest()

            val consumerOversikthendelseRetry = KafkaConsumer<String, String>(consumerPropertiesOversikthendelseRetry)
            consumerOversikthendelseRetry.subscribe(listOf(oversikthendelseRetryTopic))

            val partition = 0
            val oversikthendelseRetryTopicPartition = TopicPartition(oversikthendelseRetryTopic, partition)

            it("should resend KOversikthendelseRetry to topic when it has not exceeded retry limit and is not ready for retry") {
                val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV(ARBEIDSTAKER_FNR)
                val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
                val createdPersonOppgaveIdPair = database.connection.createPersonOppgave(
                    kOppfolgingsplanLPSNAV,
                    personOppgaveType
                )

                val kOversikthendelseRetry = generateKOversikthendelseRetry.copy(
                    fnr = ARBEIDSTAKER_FNR.value,
                    created = LocalDateTime.now(),
                    retryTime = LocalDateTime.now().plusHours(1),
                    retriedCount = 0,
                    personOppgaveId = createdPersonOppgaveIdPair.first,
                    personOppgaveUUID = createdPersonOppgaveIdPair.second.toString()
                )
                val kOversiktHendelseRetryJson = objectMapper.writeValueAsString(kOversikthendelseRetry)
                val oversiktHendelseRetryRecord = ConsumerRecord(
                    oversikthendelseRetryTopic,
                    partition,
                    1,
                    "something",
                    kOversiktHendelseRetryJson
                )

                val mockConsumerOversikthendelseRetry = mockk<KafkaConsumer<String, String>>()
                justRun { mockConsumerOversikthendelseRetry.commitSync() }
                every { mockConsumerOversikthendelseRetry.assignment() } returns emptySet()
                every { mockConsumerOversikthendelseRetry.poll(Duration.ofMillis(1000)) } returns ConsumerRecords(
                    mapOf(oversikthendelseRetryTopicPartition to listOf(oversiktHendelseRetryRecord))
                )

                runBlocking {
                    pollAndProcessOversikthendelseRetryTopic(
                        kafkaConsumer = mockConsumerOversikthendelseRetry,
                        oversikthendelseRetryService = oversikthendelseRetryService
                    )
                }

                val personOppgaveList = database.connection.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                personOppgaveList.size shouldBeEqualTo 1

                val personOppgave = personOppgaveList.first()

                personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                personOppgave.virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                personOppgave.type shouldBeEqualTo personOppgaveType.name
                personOppgave.referanseUuid shouldBeEqualTo UUID.fromString(kOppfolgingsplanLPSNAV.getUuid())
                personOppgave.oversikthendelseTidspunkt.shouldBeNull()

                val messagesKOversikthendelse: ArrayList<KOversikthendelse> = arrayListOf()
                consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                    val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                    messagesKOversikthendelse.add(consumedOversikthendelse)
                }
                messagesKOversikthendelse.size shouldBeEqualTo 0

                verify(exactly = 1) {
                    mockOversikthendelseRetryProducer.sendAgainOversikthendelseRetry(
                        kOversikthendelseRetry
                    )
                }
            }

            it("should resend KOversikthendelseRetry when retried and failed due to missing behandlendeEnhet") {
                val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV(ARBEIDSTAKER_2_FNR)
                val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
                val createdPersonOppgaveId = database.connection.createPersonOppgave(
                    kOppfolgingsplanLPSNAV,
                    personOppgaveType
                ).first

                val kOversikthendelseRetry = generateKOversikthendelseRetry.copy(
                    fnr = ARBEIDSTAKER_2_FNR.value,
                    created = LocalDateTime.now().minusHours(RETRY_OVERSIKTHENDELSE_INTERVAL_MINUTES).minusMinutes(1),
                    retryTime = LocalDateTime.now().minusMinutes(1),
                    retriedCount = 0,
                    personOppgaveId = createdPersonOppgaveId
                )
                val kOversiktHendelseRetryJson = objectMapper.writeValueAsString(kOversikthendelseRetry)
                val oversiktHendelseRetryRecord = ConsumerRecord(
                    oversikthendelseRetryTopic,
                    partition,
                    1,
                    "something",
                    kOversiktHendelseRetryJson
                )

                val mockConsumerOversikthendelseRetry = mockk<KafkaConsumer<String, String>>()
                justRun { mockConsumerOversikthendelseRetry.commitSync() }
                every { mockConsumerOversikthendelseRetry.assignment() } returns emptySet()
                every { mockConsumerOversikthendelseRetry.poll(Duration.ofMillis(1000)) } returns ConsumerRecords(
                    mapOf(oversikthendelseRetryTopicPartition to listOf(oversiktHendelseRetryRecord))
                )

                runBlocking {
                    pollAndProcessOversikthendelseRetryTopic(
                        kafkaConsumer = mockConsumerOversikthendelseRetry,
                        oversikthendelseRetryService = oversikthendelseRetryService
                    )
                }

                val personOppgaveList = database.connection.getPersonOppgaveList(ARBEIDSTAKER_2_FNR)

                personOppgaveList.size shouldBeEqualTo 1

                val personOppgave = personOppgaveList.first()

                personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.getFodselsnummer()
                personOppgave.virksomhetsnummer shouldBeEqualTo kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                personOppgave.type shouldBeEqualTo personOppgaveType.name
                personOppgave.referanseUuid shouldBeEqualTo UUID.fromString(kOppfolgingsplanLPSNAV.getUuid())
                personOppgave.oversikthendelseTidspunkt.shouldBeNull()

                val messagesKOversikthendelse: ArrayList<KOversikthendelse> = arrayListOf()
                consumerOversikthendelse.poll(Duration.ofMillis(5000)).forEach {
                    val consumedOversikthendelse: KOversikthendelse = objectMapper.readValue(it.value())
                    messagesKOversikthendelse.add(consumedOversikthendelse)
                }
                messagesKOversikthendelse.size shouldBeEqualTo 0

                verify(exactly = 1) {
                    mockOversikthendelseRetryProducer.sendRetriedOversikthendelseRetry(
                        kOversikthendelseRetry
                    )
                }
            }
        }
    }
})
