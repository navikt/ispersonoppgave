package no.nav.syfo.kafka

import no.nav.syfo.EnvironmentKafka
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

inline fun <reified Deserializer> kafkaAivenConsumerConfig(
    environmentKafka: EnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConfig(environmentKafka))
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false" // we commit manually if db persistence is successful
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1000"
        this[ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG] = "" + (10 * 1024 * 1024)
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = Deserializer::class.java.canonicalName
        this[ConsumerConfig.GROUP_ID_CONFIG] = "ispersonoppgave-v1"
    }
}

fun kafkaAivenProducerConfig(
    environmentKafka: EnvironmentKafka,
): Properties {
    return Properties().apply {
        putAll(commonKafkaAivenConfig(environmentKafka))
        this[ProducerConfig.ACKS_CONFIG] = "all"
        this[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
        this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
        this[ProducerConfig.MAX_BLOCK_MS_CONFIG] = "15000"
        this[ProducerConfig.RETRIES_CONFIG] = "100000"
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java.canonicalName
    }
}

fun commonKafkaAivenConfig(
    environmentKafka: EnvironmentKafka,
) = Properties().apply {
    this[SaslConfigs.SASL_MECHANISM] = "PLAIN"
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = environmentKafka.aivenBootstrapServers
    this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = environmentKafka.aivenSecurityProtocol
    this[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
    this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "jks"
    this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
    this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = environmentKafka.aivenTruststoreLocation
    this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = environmentKafka.aivenCredstorePassword
    this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = environmentKafka.aivenKeystoreLocation
    this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = environmentKafka.aivenCredstorePassword
    this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = environmentKafka.aivenCredstorePassword
}
