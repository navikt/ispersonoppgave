package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.kafka.launchKafkaTask

const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"

fun launchKafkaTaskIdenthendelse(
    applicationState: ApplicationState,
    environment: Environment,
    kafkaIdenthendelseConsumer: IdenthendelseConsumer,
) {
    val consumerProperties = kafkaIdenthendelseConsumerConfig(environment.kafka)
    launchKafkaTask(
        applicationState = applicationState,
        consumerProperties = consumerProperties,
        topics = listOf(PDL_AKTOR_TOPIC),
        kafkaConsumerService = kafkaIdenthendelseConsumer,
    )
}
