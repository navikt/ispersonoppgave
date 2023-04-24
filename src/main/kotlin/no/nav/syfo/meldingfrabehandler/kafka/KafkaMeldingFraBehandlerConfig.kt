package no.nav.syfo.meldingfrabehandler.kafka

import no.nav.syfo.EnvironmentKafka
import no.nav.syfo.kafka.kafkaAivenConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.*

fun kafkaMeldingFraBehandlerConfig(environmentKafka: EnvironmentKafka): Properties {
    return Properties().apply {
        putAll(kafkaAivenConsumerConfig(environmentKafka))
        this[ConsumerConfig.GROUP_ID_CONFIG] = "ispersonoppgave-v1"
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KMeldingFraBehandlerDeserializer::class.java.canonicalName
    }
}
