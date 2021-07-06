package no.nav.syfo.oversikthendelse.retry

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.server.testing.*
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.common.KafkaEnvironment
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.client.sts.StsRestClient
import no.nav.syfo.kafka.*
import no.nav.syfo.oversikthendelse.OVERSIKTHENDELSE_TOPIC
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_2_FNR
import no.nav.syfo.testutil.generator.generateKOversikthendelseRetry
import no.nav.syfo.testutil.mock.BehandlendeEnhetMock
import no.nav.syfo.testutil.mock.StsRestMock
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@InternalAPI
object KafkaOversikthendelseRetrySpek : Spek({

    with(TestApplicationEngine()) {
        start()

        val oversikthendelseRetryTopic = OVERSIKTHENDELSE_RETRY_TOPIC
        val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            withSchemaRegistry = false,
            topicNames = listOf(
                oversikthendelseRetryTopic,
                OVERSIKTHENDELSE_TOPIC
            )
        )
        val env = testEnvironment(
            embeddedEnvironment.brokersURL
        )

        val vaultSecrets = vaultSecrets

        val database = TestDB()

        val stsRestMock = StsRestMock()
        val stsRestClient = StsRestClient(
            baseUrl = stsRestMock.url,
            username = vaultSecrets.serviceuserUsername,
            password = vaultSecrets.serviceuserPassword
        )

        val behandlendeEnhetMock = BehandlendeEnhetMock()
        val behandlendeEnhetClient = BehandlendeEnhetClient(
            baseUrl = behandlendeEnhetMock.url,
            stsRestClient = stsRestClient
        )

        val oversikthendelseProducerProperties = kafkaProducerConfig(env, vaultSecrets)
            .overrideForTest()
        val oversikthendelseRecordProducer = KafkaProducer<String, KOversikthendelse>(oversikthendelseProducerProperties)
        val oversikthendelseProducer = OversikthendelseProducer(oversikthendelseRecordProducer)

        val consumerPropertiesOversikthendelse = kafkaConsumerConfig(env, vaultSecrets)
            .overrideForTest()
            .apply {
                put("specific.avro.reader", false)
                put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
                put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            }
        val consumerOversikthendelse = KafkaConsumer<String, String>(consumerPropertiesOversikthendelse)
        consumerOversikthendelse.subscribe(listOf(OVERSIKTHENDELSE_TOPIC))

        beforeGroup {
            embeddedEnvironment.start()

            behandlendeEnhetMock.server.start()
            stsRestMock.server.start()
        }

        afterGroup {
            embeddedEnvironment.tearDown()

            database.stop()
            behandlendeEnhetMock.server.stop(1L, 10L)
            stsRestMock.server.stop(1L, 10L)
        }

        afterEachTest {
            database.connection.dropData()
        }

        describe("Read and process KOversikthendelseRetry") {
            val oversikthendelseRetryProducerProperties = kafkaProducerConfig(env, vaultSecrets)
                .overrideForTest()
            val oversikthendelseRetryRecordProducer = KafkaProducer<String, KOversikthendelseRetry>(oversikthendelseRetryProducerProperties)
            val oversikthendelseRetryProducer = OversikthendelseRetryProducer(oversikthendelseRetryRecordProducer)

            val oversikthendelseRetryService = OversikthendelseRetryService(
                behandlendeEnhetClient = behandlendeEnhetClient,
                database = database,
                oversikthendelseProducer = oversikthendelseProducer,
                oversikthendelseRetryProducer = oversikthendelseRetryProducer
            )

            val consumerPropertiesOversikthendelseRetry = kafkaConsumerOversikthendelseRetryProperties(env, vaultSecrets)
                .overrideForTest()

            val consumerOversikthendelseRetry = KafkaConsumer<String, String>(consumerPropertiesOversikthendelseRetry)
            consumerOversikthendelseRetry.subscribe(listOf(oversikthendelseRetryTopic))

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
                messages.first().enhetId shouldBeEqualTo behandlendeEnhetMock.behandlendeEnhet.enhetId
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

            val consumerPropertiesOversikthendelseRetry = kafkaConsumerOversikthendelseRetryProperties(env, vaultSecrets)
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

                verify(exactly = 1) { mockOversikthendelseRetryProducer.sendAgainOversikthendelseRetry(kOversikthendelseRetry) }
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

                verify(exactly = 1) { mockOversikthendelseRetryProducer.sendRetriedOversikthendelseRetry(kOversikthendelseRetry) }
            }
        }
    }
})

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}
