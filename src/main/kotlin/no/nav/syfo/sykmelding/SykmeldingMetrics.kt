package no.nav.syfo.sykmelding

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.*

const val MOTTATT_SYKMELDING = "${METRICS_NS}_mottatt_sykmelding_count"
const val MOTTATT_SYKMELDING_CREATED_PERSONOPPGAVE = "${METRICS_NS}_mottatt_sykmelding_personoppgave_count"
const val MOTTATT_SYKMELDING_BESKRIV_BISTAND_NAV_IRRELEVANT = "${METRICS_NS}_mottatt_sykmelding_beskriv_bistand_nav_irrelevant_count"
const val MOTTATT_SYKMELDING_TILTAK_NAV_IRRELEVANT = "${METRICS_NS}_mottatt_sykmelding_tiltak_nav_irrelevant_count"
const val MOTTATT_SYKMELDING_ANDRE_TILTAK_IRRELEVANT = "${METRICS_NS}_mottatt_sykmelding_andre_tiltak_irrelevant_count"

val COUNT_MOTTATT_SYKMELDING: Counter = Counter
    .builder(MOTTATT_SYKMELDING)
    .description("Counts the number of received sykmelding")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_SUCCESS: Counter = Counter
    .builder(MOTTATT_SYKMELDING_CREATED_PERSONOPPGAVE)
    .description("Counts the number of received sykmelding that created personoppgave")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_BESKRIV_BISTAND_NAV_IRRELEVANT: Counter = Counter
    .builder(MOTTATT_SYKMELDING_BESKRIV_BISTAND_NAV_IRRELEVANT)
    .description("Counts the number of received sykmelding with beskriv bistand Nav that is irrelevant for oppgave")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_TILTAK_NAV_IRRELEVANT: Counter = Counter
    .builder(MOTTATT_SYKMELDING_TILTAK_NAV_IRRELEVANT)
    .description("Counts the number of received sykmelding with tiltak Nav that is irrelevant for oppgave")
    .register(METRICS_REGISTRY)

val COUNT_MOTTATT_SYKMELDING_ANDRE_TILTAK_IRRELEVANT: Counter = Counter
    .builder(MOTTATT_SYKMELDING_ANDRE_TILTAK_IRRELEVANT)
    .description("Counts the number of received sykmelding with andre tiltak that is irrelevant for oppgave")
    .register(METRICS_REGISTRY)
