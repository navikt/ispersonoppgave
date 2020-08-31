package no.nav.syfo.kafka

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.*
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.util.callIdArgument
import no.nav.syfo.util.kafkaCallId
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

private val LOG: Logger = LoggerFactory.getLogger("no.nav.syfo.Kafka")

const val OPPFOLGINGSPLAN_LPS_NAV_TOPIC = "aapen-syfo-oppfolgingsplan-lps-nav-v1"

fun kafkaConsumerConfig(
    env: Environment,
    vaultSecrets: VaultSecrets
): Properties {
    return Properties().apply {
        this["group.id"] = "${env.applicationName}-consumer"
        this["auto.offset.reset"] = "earliest"
        this["retries"] = "2"
        this["security.protocol"] = "SASL_SSL"
        this["sasl.mechanism"] = "PLAIN"
        this["schema.registry.url"] = "http://kafka-schema-registry.tpa.svc.nais.local:8081"
        this["specific.avro.reader"] = true
        this["key.deserializer"] = "io.confluent.kafka.serializers.KafkaAvroDeserializer"
        this["value.deserializer"] = "io.confluent.kafka.serializers.KafkaAvroDeserializer"
        this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${vaultSecrets.serviceuserUsername}\" password=\"${vaultSecrets.serviceuserPassword}\";"
        this["bootstrap.servers"] = env.kafkaBootstrapServers
    }
}

suspend fun CoroutineScope.setupKafka(
    vaultSecrets: VaultSecrets
) {
    LOG.info("Setting up kafka consumer")

    launchListeners(state, kafkaConsumerConfig(env, vaultSecrets))
}

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListeners(
    applicationState: ApplicationState,
    consumerProperties: Properties
) {
    val kafkaConsumerOppfolgingsplanLPSNAV = KafkaConsumer<String, KOppfolgingsplanLPSNAV>(consumerProperties)

    val subscriptionCallback = object : ConsumerRebalanceListener {
        override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>?) {
            if (false) {
                log.info("onPartitionsAssigned called for ${partitions?.size ?: 0} partitions. Seeking to beginning.")
                kafkaConsumerOppfolgingsplanLPSNAV.seekToBeginning(partitions)
            }
        }

        override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>?) {}
    }

    kafkaConsumerOppfolgingsplanLPSNAV.subscribe(
        listOf(OPPFOLGINGSPLAN_LPS_NAV_TOPIC),
        subscriptionCallback
    )

    createListener(applicationState) {
        blockingApplicationLogic(applicationState, kafkaConsumerOppfolgingsplanLPSNAV)
    }

    applicationState.initialized = true
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, KOppfolgingsplanLPSNAV>
) {
    while (applicationState.running) {
        var logValues = arrayOf(
            StructuredArguments.keyValue("id", "missing"),
            StructuredArguments.keyValue("timestamp", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            val callId = kafkaCallId()
            val kOppfolgingsplanLPSNAV: KOppfolgingsplanLPSNAV = it.value()
            logValues = arrayOf(
                StructuredArguments.keyValue("id", it.key()),
                StructuredArguments.keyValue("timestamp", it.timestamp())
            )
            LOG.info("Received KOppfolgingsplanLPSNAV, ready to process, $logKeys, {}", *logValues, callIdArgument(callId))
        }
        delay(100)
    }
}

fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    launch {
        try {
            action()
        } finally {
            applicationState.running = false
        }
    }
