package no.nav.syfo.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

fun kafkaConsumerConfig(
    env: Environment,
    vaultSecrets: VaultSecrets,
): Properties {
    return Properties().apply {
        this["group.id"] = "${env.applicationName}-consumer"
        this["auto.offset.reset"] = "earliest"
        this["retries"] = "2"
        this["security.protocol"] = "SASL_SSL"
        this["sasl.mechanism"] = "PLAIN"
        this["schema.registry.url"] = "http://kafka-schema-registry.tpa.svc.nais.local:8081"
        this["specific.avro.reader"] = true
        this["key.deserializer"] = KafkaAvroDeserializer::class.java.canonicalName
        this["value.deserializer"] = KafkaAvroDeserializer::class.java.canonicalName
        this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${vaultSecrets.serviceuserUsername}\" password=\"${vaultSecrets.serviceuserPassword}\";"
        this["bootstrap.servers"] = env.kafkaBootstrapServers
    }
}

fun kafkaConsumerOversikthendelseRetryProperties(
    env: Environment,
    vaultSecrets: VaultSecrets,
) = Properties().apply {
    this[ConsumerConfig.GROUP_ID_CONFIG] = "${env.applicationName}-consumer-retry"
    this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
    this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
    this[CommonClientConfigs.RETRIES_CONFIG] = "2"
    this["acks"] = "all"
    this["security.protocol"] = "SASL_SSL"
    this["sasl.mechanism"] = "PLAIN"
    this["schema.registry.url"] = "http://kafka-schema-registry.tpa:8081"
    this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
    this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName

    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
        "username=\"${vaultSecrets.serviceuserUsername}\" password=\"${vaultSecrets.serviceuserPassword}\";"
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.kafkaBootstrapServers
}

fun kafkaProducerConfig(
    env: Environment,
    vaultSecrets: VaultSecrets
): Properties {
    return Properties().apply {
        this["group.id"] = "${env.applicationName}-producer"
        this["security.protocol"] = "SASL_SSL"
        this["schema.registry.url"] = "http://kafka-schema-registry.tpa.svc.nais.local:8081"
        this["sasl.mechanism"] = "PLAIN"
        this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${vaultSecrets.serviceuserUsername}\" password=\"${vaultSecrets.serviceuserPassword}\";"
        this["key.serializer"] = StringSerializer::class.java.canonicalName
        this["value.serializer"] = JacksonKafkaSerializer::class.java.canonicalName
        this["bootstrap.servers"] = env.kafkaBootstrapServers
    }
}
