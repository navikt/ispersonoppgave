package no.nav.syfo.oversikthendelse.retry

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.kafka.kafkaConsumerOversikthendelseRetryProperties
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.kafkaCallId
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

private val LOG: Logger = LoggerFactory.getLogger("no.nav.syfo.oversikthendelse.retry")

suspend fun blockingApplicationLogicOversikthendelseRetry(
    applicationState: ApplicationState,
    environment: Environment,
    oversikthendelseRetryService: OversikthendelseRetryService,
) {
    LOG.info("Setting up kafka consumer OversikthendelseRetry")

    val consumerOversikthendelseRetryProperties = kafkaConsumerOversikthendelseRetryProperties(env = environment)
    val kafkaConsumerOversikthendelseRetry = KafkaConsumer<String, String>(consumerOversikthendelseRetryProperties)

    kafkaConsumerOversikthendelseRetry.subscribe(
        listOf(OVERSIKTHENDELSE_RETRY_TOPIC)
    )

    while (applicationState.ready) {
        pollAndProcessOversikthendelseRetryTopic(
            kafkaConsumer = kafkaConsumerOversikthendelseRetry,
            oversikthendelseRetryService = oversikthendelseRetryService,
        )
        delay(5000L)
    }
}

suspend fun pollAndProcessOversikthendelseRetryTopic(
    kafkaConsumer: KafkaConsumer<String, String>,
    oversikthendelseRetryService: OversikthendelseRetryService,
) {
    var logValues = arrayOf(
        StructuredArguments.keyValue("id", "missing"),
        StructuredArguments.keyValue("timestamp", "missing")
    )

    val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
        "{}"
    }

    val messages = kafkaConsumer.poll(Duration.ofMillis(1000))
    messages.forEach {
        val callId = kafkaCallId()
        val kOversikthendelseRetry: KOversikthendelseRetry = objectMapper.readValue(it.value())
        logValues = arrayOf(
            StructuredArguments.keyValue("id", it.key()),
            StructuredArguments.keyValue("timestamp", it.timestamp())
        )
        LOG.info("Received KOversikthendelseRetry, ready to process, $logKeys, {}", *logValues, callIdArgument(callId))
        oversikthendelseRetryService.receiveOversikthendelseRetry(kOversikthendelseRetry)
        kafkaConsumer.commitSync()
    }
}

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}
