package no.nav.syfo.behandler.kafka.sykmelding

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.*

const val MOTTATT_SYKMELDING = "${METRICS_NS}_mottatt_sykmelding_count"
const val MOTTATT_SYKMELDING_CREATED_PERSONOPPGAVE = "${METRICS_NS}_mottatt_sykmelding_personoppgave_count"
const val MOTTATT_SYKMELDING_TILTAK_NAV = "${METRICS_NS}_mottatt_sykmelding_tiltak_nav_count"
const val MOTTATT_SYKMELDING_TILTAK_ANDRE = "${METRICS_NS}_mottatt_sykmelding_tiltak_andre_count"
const val MOTTATT_SYKMELDING_UTDYPENDE = "${METRICS_NS}_mottatt_sykmelding_utdypende_count"

val COUNT_MOTTATT_SYKMELDING: Counter = Counter
    .builder(MOTTATT_SYKMELDING)
    .description("Counts the number of received sykmelding")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_SUCCESS: Counter = Counter
    .builder(MOTTATT_SYKMELDING_CREATED_PERSONOPPGAVE)
    .description("Counts the number of received sykmelding that created personoppgave")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_TILTAK_NAV: Counter = Counter
    .builder(MOTTATT_SYKMELDING_TILTAK_NAV)
    .description("Counts the number of received sykmelding with tiltak NAV")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_TILTAK_ANDRE: Counter = Counter
    .builder(MOTTATT_SYKMELDING_TILTAK_ANDRE)
    .description("Counts the number of received sykmelding with tiltak andre")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_UTDYPENDE: Counter = Counter
    .builder(MOTTATT_SYKMELDING_UTDYPENDE)
    .description("Counts the number of received sykmelding with utdypende opplysninger")
    .register(METRICS_REGISTRY)
