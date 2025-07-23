package no.nav.syfo.personoppgave.infrastructure.kafka

import org.apache.kafka.clients.consumer.KafkaConsumer

interface KafkaConsumerService<ConsumerRecordValue> {
    val pollDurationInMillis: Long
    fun pollAndProcessRecords(
        kafkaConsumer: KafkaConsumer<String, ConsumerRecordValue>,
    )
}
