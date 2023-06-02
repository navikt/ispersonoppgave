package no.nav.syfo.testutil

import no.nav.common.KafkaEnvironment
import no.nav.syfo.Environment
import no.nav.syfo.identhendelse.kafka.PDL_AKTOR_TOPIC
import no.nav.syfo.kafka.*
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.*
import no.nav.syfo.personoppgavehendelse.*
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        OPPFOLGINGSPLAN_LPS_NAV_TOPIC,
        PDL_AKTOR_TOPIC,
    ),
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
    val consumerPropertiesOppfolgingsplanLPS =
        kafkaAivenConsumerConfig<KOppfolgingsplanLPSDeserializer>(externalMockEnvironment.environment.kafka)
            .overrideForTest()
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
    val consumerPropertiesPersonoppgavehendelse = kafkaAivenConsumerConfig<StringDeserializer>(environment.kafka)
        .overrideForTest()
        .apply {
            put("specific.avro.reader", false)
        }
    val consumerpersonoppgavehendelse = KafkaConsumer<String, String>(consumerPropertiesPersonoppgavehendelse)
    consumerpersonoppgavehendelse.subscribe(listOf(PERSONOPPGAVEHENDELSE_TOPIC))
    return consumerpersonoppgavehendelse
}
