package no.nav.syfo.oversikthendelse.retry

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.*
import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.ApplicationState
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.kafkaCallId
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

private val LOG: Logger = LoggerFactory.getLogger("no.nav.syfo.oversikthendelse.retry")

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListenerOversikthendelseRetry(
    applicationState: ApplicationState,
    consumerOversikthendelseRetryProperties: Properties,
    oversikthendelseRetryService: OversikthendelseRetryService,
    toggleRetry: Boolean,
) {
    val kafkaConsumerOversikthendelseRetry = KafkaConsumer<String, String>(consumerOversikthendelseRetryProperties)

    kafkaConsumerOversikthendelseRetry.subscribe(
        listOf(OVERSIKTHENDELSE_RETRY_TOPIC)
    )
    createListenerOversikthendelseRetry(applicationState) {
        blockingApplicationLogicOversikthendelseRetry(
            applicationState,
            kafkaConsumerOversikthendelseRetry,
            oversikthendelseRetryService,
            toggleRetry
        )
    }
}

suspend fun blockingApplicationLogicOversikthendelseRetry(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    oversikthendelseRetryService: OversikthendelseRetryService,
    toggleRetry: Boolean
) {
    while (applicationState.running) {
        pollAndProcessOversikthendelseRetryTopic(
            kafkaConsumer = kafkaConsumer,
            oversikthendelseRetryService = oversikthendelseRetryService,
            toggleRetry = toggleRetry
        )
        delay(5000L)
    }
}

fun pollAndProcessOversikthendelseRetryTopic(
    kafkaConsumer: KafkaConsumer<String, String>,
    oversikthendelseRetryService: OversikthendelseRetryService,
    toggleRetry: Boolean
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
        if (toggleRetry) {
            oversikthendelseRetryService.receiveOversikthendelseRetry(kOversikthendelseRetry)
        } else {
            LOG.info("Retry is turned off: Skipped receival of KOversikthendelseRetry")
        }
        kafkaConsumer.commitSync()
    }
}

fun CoroutineScope.createListenerOversikthendelseRetry(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job =
    launch {
        try {
            action()
        } finally {
            applicationState.running = false
        }
    }

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}
