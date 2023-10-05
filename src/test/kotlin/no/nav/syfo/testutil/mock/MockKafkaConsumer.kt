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
    recordValue2: ConsumerRecordValue? = null,
    recordKey: String = UUID.randomUUID().toString(),
    recordKey2: String = UUID.randomUUID().toString(),
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
        recordValue,
    )
    val consumerRecord2 = if (recordValue2 == null) null else
        ConsumerRecord(
            topic,
            0,
            2,
            recordKey2,
            recordValue2,
        )
    val consumerRecordList = if (consumerRecord2 == null) listOf(consumerRecord) else listOf(consumerRecord, consumerRecord2)
    val consumerRecords = ConsumerRecords(mapOf(topicPartition to consumerRecordList))
    every { this@mockPollConsumerRecords.poll(any<Duration>()) } returns consumerRecords
}
