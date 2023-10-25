package no.nav.syfo.kafka

import no.nav.syfo.ApplicationState
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

inline fun <reified ConsumerRecordValue> launchKafkaTask(
    applicationState: ApplicationState,
    topics: List<String>,
    consumerProperties: Properties,
    kafkaConsumerService: KafkaConsumerService<ConsumerRecordValue>,
) {
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        log.info("Setting up kafka consumer ${kafkaConsumerService::class.simpleName} for topics $topics")

        val kafkaConsumer = KafkaConsumer<String, ConsumerRecordValue>(consumerProperties)
        kafkaConsumer.subscribe(topics)

        while (applicationState.ready) {
            if (kafkaConsumer.subscription().isEmpty()) {
                kafkaConsumer.subscribe(topics)
            }
            kafkaConsumerService.pollAndProcessRecords(kafkaConsumer)
        }
    }
}
