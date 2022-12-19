package no.nav.syfo.identhendelse.kafka

import kotlinx.coroutines.runBlocking
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer

const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"

fun consumeIdenthendelse(
    applicationState: ApplicationState,
    environment: Environment,
    kafkaIdenthendelseConsumerService: IdenthendelseConsumerService,
) {
    val kafkaConfig = kafkaIdenthendelseConsumerConfig(environment.kafka)
    val kafkaConsumer = KafkaConsumer<String, GenericRecord>(kafkaConfig)

    kafkaConsumer.subscribe(
        listOf(PDL_AKTOR_TOPIC)
    )
    while (applicationState.ready) {
        runBlocking {
            kafkaIdenthendelseConsumerService.pollAndProcessRecords(
                kafkaConsumer = kafkaConsumer,
            )
        }
    }
}
