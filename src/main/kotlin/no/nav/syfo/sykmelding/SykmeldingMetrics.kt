package no.nav.syfo.sykmelding

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.*

const val MOTTATT_SYKMELDING = "${METRICS_NS}_mottatt_sykmelding_count"
const val MOTTATT_SYKMELDING_CREATED_PERSONOPPGAVE = "${METRICS_NS}_mottatt_sykmelding_personoppgave_count"
const val MOTTATT_SYKMELDING_SKIPPED_IRRELEVANT_TEXT = "${METRICS_NS}_mottatt_sykmelding_skipped_personoppgave_irrelevant_count"
const val MOTTATT_SYKMELDING_SHORT_TEXT = "${METRICS_NS}_mottatt_sykmelding_personoppgave_short_text_count"

val COUNT_MOTTATT_SYKMELDING: Counter = Counter
    .builder(MOTTATT_SYKMELDING)
    .description("Counts the number of received sykmelding")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_SUCCESS: Counter = Counter
    .builder(MOTTATT_SYKMELDING_CREATED_PERSONOPPGAVE)
    .description("Counts the number of received sykmelding that created personoppgave")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_SKIPPED_IRRELEVANT_TEXT: Counter = Counter
    .builder(MOTTATT_SYKMELDING_SKIPPED_IRRELEVANT_TEXT)
    .description("Counts the number of received sykmelding that skipped creating personoppgave due to irrelevant text")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_SHORT_TEXT: Counter = Counter
    .builder(MOTTATT_SYKMELDING_SHORT_TEXT)
    .description("Counts the number of received sykmelding that created personoppgave with short text")
    .register(METRICS_REGISTRY)
