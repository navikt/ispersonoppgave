package no.nav.syfo.dialogmotestatusendring.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_BASE = "${METRICS_NS}_kafka_consumer_dialogmote_statusendring"
const val KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_READ = "${KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_BASE}_read"
const val KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_TOMBSTONE = "${KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_BASE}_tombstone"
const val KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_CREATE = "${KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_BASE}_create"
const val KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_UPDATE = "${KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_BASE}_update"

val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_READ: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_READ)
        .description("Counts the number of reads from topic - dialogmote-statusendring")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_TOMBSTONE: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_TOMBSTONE)
        .description("Counts the number of tombstones received from topic - dialogmote-statusendring")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_CREATED_PERSONOVERSIKT_STATUS: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_CREATE)
        .description("Counts the number of personoversikt-status created from reading topic - dialogmote-statusendring")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_UPDATED_PERSONOVERSIKT_STATUS: Counter =
    Counter.builder(KAFKA_CONSUMER_DIALOGMOTE_STATUSENDRING_UPDATE)
        .description("Counts the number of personoversikt-status updated from reading topic - dialogmote-statusendring")
        .register(METRICS_REGISTRY)
