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

const val OVERSIKTHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT = "oversikthendelse_oppfolgingsplanlps_bistand_mottatt_sent"
val COUNT_OVERSIKTHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(OVERSIKTHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT)
    .help("Counts the number of Oversikthendelse with OversikthendelseType OPPFOLGINGSPLANLPS_BISTAND_MOTTATT created from a KOppfolgingsplanLPS")
    .register()

const val CALL_BEHANDLENDEENHET_SUCCESS = "call_behandlendeenhet_success_count"
const val CALL_BEHANDLENDEENHET_FAIL = "call_behandlendeenhet_fail_count"
const val CALL_BEHANDLENDEENHET_EMPTY = "call_behandlendeenhet_empty_count"
val COUNT_CALL_BEHANDLENDEENHET_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_BEHANDLENDEENHET_SUCCESS)
    .help("Counts the number of successful calls to syfobehandlendeenhet")
    .register()
val COUNT_CALL_BEHANDLENDEENHET_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_BEHANDLENDEENHET_FAIL)
    .help("Counts the number of failed calls to syfobehandlendeenhet")
    .register()
val COUNT_CALL_BEHANDLENDEENHET_EMPTY: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_BEHANDLENDEENHET_EMPTY)
    .help("Counts the number of responses from syfobehandlendeenhet with status 204 received")
    .register()

const val CALL_TILGANGSKONTROLL_PERSON_SUCCESS = "call_tilgangskontroll_person_success_count"
const val CALL_TILGANGSKONTROLL_PERSON_FAIL = "call_tilgangskontroll_person_fail_count"
val COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_PERSON_SUCCESS)
    .help("Counts the number of successful calls to syfo-tilgangskontroll - person")
    .register()
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_PERSON_FAIL)
    .help("Counts the number of failed calls to syfo-tilgangskontroll - person")
    .register()

const val OPPFOLGINGSTILFELLE_SKIPPED_BEHANDLENDEENHET = "oppfolgingstilfelle_skipped_behandlendeenhet_count"
val COUNT_OPPFOLGINGSTILFELLE_SKIPPED_BEHANDLENDEENHET: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(OPPFOLGINGSTILFELLE_SKIPPED_BEHANDLENDEENHET)
    .help("Counts the number of Oppfolgingstilfeller skipped because BehandlendeEnhet was not found")
    .register()
