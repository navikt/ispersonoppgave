package no.nav.syfo.testutil

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import no.nav.common.KafkaEnvironment
import no.nav.syfo.Environment
import no.nav.syfo.kafka.*
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.personoppgavehendelse.*
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
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

fun testPersonoppgavehendelseProducer(
    environment: Environment,
): PersonoppgavehendelseProducer {
    val personoppgavehendelseProducerProperties = kafkaAivenProducerConfig(environmentKafka = environment.kafka)
        .overrideForTest()
    val personoppgavehendelseRecordProducer =
        KafkaProducer<String, KPersonoppgavehendelse>(personoppgavehendelseProducerProperties)
    return PersonoppgavehendelseProducer(personoppgavehendelseRecordProducer)
}

fun testPersonoppgavehendelseConsumer(
    environment: Environment,
): KafkaConsumer<String, String> {
    val consumerPropertiesPersonoppgavehendelse = kafkaConsumerConfig(env = environment)
        .overrideForTest()
        .apply {
            put("specific.avro.reader", false)
            put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        }
    val consumerpersonoppgavehendelse = KafkaConsumer<String, String>(consumerPropertiesPersonoppgavehendelse)
    consumerpersonoppgavehendelse.subscribe(listOf(PERSONOPPGAVEHENDELSE_TOPIC))
    return consumerpersonoppgavehendelse
}
