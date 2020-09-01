package no.nav.syfo.metric

import io.prometheus.client.Counter

const val METRICS_NS = "ispersonoppgave"

const val PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED = "person_oppgave_oppfolgingsplanlps_created_count"
const val PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED = "person_oppgave_oppfolgingsplanlps_already_created_count"
const val PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND = "person_oppgave_oppfolgingsplanlps_no_bistand_count"

val COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED)
    .help("Counts the number of PERSON_OPPGAVE created from a KOppfolgingsplanLPS")
    .register()
val COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED)
    .help("Counts the number KOppfolgingsplanLPS skipped due to already exisiting PersonOppgave")
    .register()
val COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND)
    .help("Counts the number KOppfolgingsplanLPS skipped due to no BehovForBistandFraNav")
    .register()
