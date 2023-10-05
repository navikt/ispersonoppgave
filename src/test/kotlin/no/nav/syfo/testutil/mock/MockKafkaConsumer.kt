package no.nav.syfo.testutil.mock

import io.mockk.every
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.*

fun <ConsumerRecordValue> KafkaConsumer<String, ConsumerRecordValue>.mockPollConsumerRecords(
    recordValue: ConsumerRecordValue?,
    recordKey: String = UUID.randomUUID().toString(),
    topic: String = "topic",
) {
    val topicPartition = TopicPartition(
        topic,
        0
    )
    val consumerRecord = ConsumerRecord(
        topic,
        0,
        1,
        recordKey,
        recordValue
    )
    val consumerRecords = ConsumerRecords(mapOf(topicPartition to listOf(consumerRecord)))
    every { this@mockPollConsumerRecords.poll(any<Duration>()) } returns consumerRecords
}
