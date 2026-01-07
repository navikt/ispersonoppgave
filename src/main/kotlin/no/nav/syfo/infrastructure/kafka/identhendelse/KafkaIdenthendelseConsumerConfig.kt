package no.nav.syfo.infrastructure.kafka.identhendelse

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.syfo.EnvironmentKafka
import no.nav.syfo.infrastructure.kafka.kafkaAivenConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.*

fun kafkaIdenthendelseConsumerConfig(
    environmentKafka: EnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(kafkaAivenConsumerConfig<KafkaAvroDeserializer>(environmentKafka))
        this[ConsumerConfig.GROUP_ID_CONFIG] = "ispersonoppgave-v2"
        this[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = environmentKafka.aivenSchemaRegistryUrl
        this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = false
        this[KafkaAvroDeserializerConfig.USER_INFO_CONFIG] = "${environmentKafka.aivenRegistryUser}:${environmentKafka.aivenRegistryPassword}"
        this[KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
    }
}
