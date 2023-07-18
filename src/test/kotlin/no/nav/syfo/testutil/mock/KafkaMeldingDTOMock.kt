package no.nav.syfo.testutil.mock

import io.mockk.every
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.*

fun mockReceiveMeldingDTO(
    kMeldingDTO: KMeldingDTO?,
    kafkaConsumer: KafkaConsumer<String, KMeldingDTO>,
) {
    every { kafkaConsumer.poll(any<Duration>()) } returns ConsumerRecords(
        mapOf(
            mockTopicPartition() to listOf(
                meldingDTORecord(
                    kMeldingDTO,
                ),
            )
        )
    )
}

fun mockTopicPartition() = TopicPartition(
    "topicnavn",
    0
)

fun meldingDTORecord(
    kMeldingDTO: KMeldingDTO?,
) = ConsumerRecord(
    "topicnavn",
    0,
    1,
    UUID.randomUUID().toString(),
    kMeldingDTO
)
