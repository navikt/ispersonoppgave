package no.nav.syfo.testutil

import no.nav.common.KafkaEnvironment
import no.nav.syfo.oversikthendelse.OVERSIKTHENDELSE_TOPIC
import no.nav.syfo.oversikthendelse.retry.OVERSIKTHENDELSE_RETRY_TOPIC

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        OVERSIKTHENDELSE_TOPIC,
        OVERSIKTHENDELSE_RETRY_TOPIC,
    )
) = KafkaEnvironment(
    autoStart = autoStart,
    withSchemaRegistry = withSchemaRegistry,
    topicNames = topicNames,
)
