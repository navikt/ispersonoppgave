package no.nav.syfo.testutil

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import no.nav.common.KafkaEnvironment
import no.nav.syfo.Environment
import no.nav.syfo.kafka.*
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.oversikthendelse.OVERSIKTHENDELSE_TOPIC
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.retry.*
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.OPPFOLGINGSPLAN_LPS_NAV_TOPIC
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.config.SaslConfigs
import java.util.*

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        OVERSIKTHENDELSE_TOPIC,
        OVERSIKTHENDELSE_RETRY_TOPIC,
        OPPFOLGINGSPLAN_LPS_NAV_TOPIC,
    )
) = KafkaEnvironment(
    autoStart = autoStart,
    withSchemaRegistry = withSchemaRegistry,
    topicNames = topicNames,
)

fun Properties.overrideForTest(): Properties = apply {
    remove(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG)
    remove(SaslConfigs.SASL_MECHANISM)
}

fun testOppfolgingsplanLPSConsumer(
    externalMockEnvironment: ExternalMockEnvironment,
): KafkaConsumer<String, KOppfolgingsplanLPSNAV> {
    val consumerPropertiesOppfolgingsplanLPS = kafkaConsumerConfig(env = externalMockEnvironment.environment)
        .overrideForTest()
        .apply {
            put(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                externalMockEnvironment.embeddedEnvironment.schemaRegistry!!.url
            )
        }
    val consumerOppfolgingsplanLPS = KafkaConsumer<String, KOppfolgingsplanLPSNAV>(consumerPropertiesOppfolgingsplanLPS)
    consumerOppfolgingsplanLPS.subscribe(listOf(OPPFOLGINGSPLAN_LPS_NAV_TOPIC))
    return consumerOppfolgingsplanLPS
}

fun testOversikthendelseProducer(
    environment: Environment,
): OversikthendelseProducer {
    val oversikthendelseProducerProperties = kafkaProducerConfig(env = environment)
        .overrideForTest()
    val oversikthendelseRecordProducer =
        KafkaProducer<String, KOversikthendelse>(oversikthendelseProducerProperties)
    return OversikthendelseProducer(oversikthendelseRecordProducer)
}

fun testOversikthendelseConsumer(
    environment: Environment,
): KafkaConsumer<String, String> {
    val consumerPropertiesOversikthendelse = kafkaConsumerConfig(env = environment)
        .overrideForTest()
        .apply {
            put("specific.avro.reader", false)
            put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        }
    val consumerOversikthendelse = KafkaConsumer<String, String>(consumerPropertiesOversikthendelse)
    consumerOversikthendelse.subscribe(listOf(OVERSIKTHENDELSE_TOPIC))
    return consumerOversikthendelse
}

fun testOversikthendelseRetryProducer(
    environment: Environment,
): OversikthendelseRetryProducer {
    val oversikthendelseRetryProducerProperties = kafkaProducerConfig(env = environment)
        .overrideForTest()
    val oversikthendelseRetryRecordProducer =
        KafkaProducer<String, KOversikthendelseRetry>(oversikthendelseRetryProducerProperties)
    return OversikthendelseRetryProducer(oversikthendelseRetryRecordProducer)
}

fun testOversikthendelseRetryConsumer(
    environment: Environment,
): KafkaConsumer<String, String> {
    val consumerPropertiesOversikthendelseRetry = kafkaConsumerOversikthendelseRetryProperties(env = environment)
        .overrideForTest()
    val consumerOversikthendelseRetry = KafkaConsumer<String, String>(consumerPropertiesOversikthendelseRetry)
    consumerOversikthendelseRetry.subscribe(listOf(OVERSIKTHENDELSE_RETRY_TOPIC))
    return consumerOversikthendelseRetry
}
