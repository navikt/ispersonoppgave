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
    val records = if (recordValue2 == null) {
        listOf(recordKey to recordValue)
    } else {
        listOf(recordKey to recordValue, recordKey2 to recordValue2)
    }
    mockPollConsumerRecords(records = records, topic = topic)
}

fun <ConsumerRecordValue> KafkaConsumer<String, ConsumerRecordValue>.mockPollConsumerRecords(
    records: List<Pair<String, ConsumerRecordValue?>>,
    topic: String = "topic",
) {
    val topicPartition = TopicPartition(
        topic,
        0
    )
    val consumerRecordList = records.mapIndexed { index, (key, value) ->
        ConsumerRecord(
            topic,
            0,
            index.toLong() + 1,
            key,
            value,
        )
    }
    val consumerRecords = ConsumerRecords(mapOf(topicPartition to consumerRecordList))
    every { this@mockPollConsumerRecords.poll(any<Duration>()) } returns consumerRecords
}
