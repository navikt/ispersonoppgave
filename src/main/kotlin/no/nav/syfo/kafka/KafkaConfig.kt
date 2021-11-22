package no.nav.syfo.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import no.nav.syfo.Environment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

fun kafkaConsumerConfig(
    env: Environment,
): Properties {
    return Properties().apply {
        this[ConsumerConfig.GROUP_ID_CONFIG] = "${env.applicationName}-consumer"
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        this[CommonClientConfigs.RETRIES_CONFIG] = "2"
        this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
        this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
        this["schema.registry.url"] = env.kafkaSchemaRegistryUrl
        this["specific.avro.reader"] = true
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java.canonicalName
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java.canonicalName
        this[SaslConfigs.SASL_JAAS_CONFIG] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${env.serviceuserUsername}\" password=\"${env.serviceuserPassword}\";"
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.kafkaBootstrapServers
    }
}

fun kafkaConsumerOversikthendelseRetryProperties(
    env: Environment,
) = Properties().apply {
    this[ConsumerConfig.GROUP_ID_CONFIG] = "${env.applicationName}-consumer-retry"
    this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
    this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
    this[CommonClientConfigs.RETRIES_CONFIG] = "2"
    this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
    this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
    this["schema.registry.url"] = env.kafkaSchemaRegistryUrl
    this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
    this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
    this[SaslConfigs.SASL_JAAS_CONFIG] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
        "username=\"${env.serviceuserUsername}\" password=\"${env.serviceuserPassword}\";"
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.kafkaBootstrapServers
}

fun kafkaProducerConfig(
    env: Environment,
): Properties {
    return Properties().apply {
        this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SASL_SSL"
        this["schema.registry.url"] = env.kafkaSchemaRegistryUrl
        this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
        this[SaslConfigs.SASL_JAAS_CONFIG] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${env.serviceuserUsername}\" password=\"${env.serviceuserPassword}\";"
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java.canonicalName
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.kafkaBootstrapServers
    }
}
