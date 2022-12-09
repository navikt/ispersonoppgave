package no.nav.syfo.testutil

import no.nav.common.KafkaEnvironment
import no.nav.syfo.Environment
import no.nav.syfo.kafka.*
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.*
import no.nav.syfo.personoppgavehendelse.*
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
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
): KafkaConsumer<String, KOppfolgingsplanLPS> {
    val consumerPropertiesOppfolgingsplanLPS = kafkaAivenConsumerConfig(externalMockEnvironment.environment.kafka)
        .overrideForTest()
        .apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, "ispersonoppgave-v1")
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KOppfolgingsplanLPSDeserializer::class.java.canonicalName)
        }
    val consumerOppfolgingsplanLPS = KafkaConsumer<String, KOppfolgingsplanLPS>(consumerPropertiesOppfolgingsplanLPS)
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
    val consumerPropertiesPersonoppgavehendelse = kafkaAivenConsumerConfig(environment.kafka)
        .overrideForTest()
        .apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, "ispersonoppgave-v1")
            put("specific.avro.reader", false)
            put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
            put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
        }
    val consumerpersonoppgavehendelse = KafkaConsumer<String, String>(consumerPropertiesPersonoppgavehendelse)
    consumerpersonoppgavehendelse.subscribe(listOf(PERSONOPPGAVEHENDELSE_TOPIC))
    return consumerpersonoppgavehendelse
}
