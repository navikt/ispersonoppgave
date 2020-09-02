package no.nav.syfo.kafka

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import java.util.*

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
        this["key.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"
        this["value.serializer"] = "no.nav.syfo.kafka.JacksonKafkaSerializer"
        this["bootstrap.servers"] = env.kafkaBootstrapServers
    }
}
