package no.nav.syfo.metric

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_NS = "ispersonoppgave"

val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

const val PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED = "${METRICS_NS}_person_oppgave_oppfolgingsplanlps_created_count"
const val PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED =
    "${METRICS_NS}_person_oppgave_oppfolgingsplanlps_already_created_count"
const val PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND =
    "${METRICS_NS}_person_oppgave_oppfolgingsplanlps_no_bistand_count"

val COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED: Counter =
    Counter.builder(PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED)
        .description("Counts the number of PERSON_OPPGAVE created from a KOppfolgingsplanLPS")
        .register(METRICS_REGISTRY)
val COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED: Counter =
    Counter.builder(PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED)
        .description("Counts the number KOppfolgingsplanLPS skipped due to already exisiting PersonOppgave")
        .register(METRICS_REGISTRY)
val COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND: Counter =
    Counter.builder(PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND)
        .description("Counts the number KOppfolgingsplanLPS skipped due to no BehovForBistandFraNav")
        .register(METRICS_REGISTRY)

const val OVERSIKTHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT =
    "oversikthendelse_oppfolgingsplanlps_bistand_mottatt_sent_count"
val COUNT_OVERSIKTHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT: Counter =
    Counter.builder(OVERSIKTHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT)
        .description("Counts the number of Oversikthendelse with OversikthendelseType OPPFOLGINGSPLANLPS_BISTAND_MOTTATT created from a KOppfolgingsplanLPS")
        .register(METRICS_REGISTRY)

const val CALL_BEHANDLENDEENHET_SUCCESS = "${METRICS_NS}_call_behandlendeenhet_success_count"
const val CALL_BEHANDLENDEENHET_FAIL = "${METRICS_NS}_call_behandlendeenhet_fail_count"
const val CALL_BEHANDLENDEENHET_EMPTY = "${METRICS_NS}_call_behandlendeenhet_empty_count"
val COUNT_CALL_BEHANDLENDEENHET_SUCCESS: Counter = Counter.builder(CALL_BEHANDLENDEENHET_SUCCESS)
    .description("Counts the number of successful calls to syfobehandlendeenhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_BEHANDLENDEENHET_FAIL: Counter = Counter.builder(CALL_BEHANDLENDEENHET_FAIL)
    .description("Counts the number of failed calls to syfobehandlendeenhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_BEHANDLENDEENHET_EMPTY: Counter = Counter.builder(CALL_BEHANDLENDEENHET_EMPTY)
    .description("Counts the number of responses from syfobehandlendeenhet with status 204 received")
    .register(METRICS_REGISTRY)

const val CALL_TILGANGSKONTROLL_PERSON_SUCCESS = "${METRICS_NS}_call_tilgangskontroll_person_success_count"
const val CALL_TILGANGSKONTROLL_PERSON_FAIL = "${METRICS_NS}_call_tilgangskontroll_person_fail_count"
const val CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN = "${METRICS_NS}_call_tilgangskontroll_person_forbidden_count"
val COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_SUCCESS)
    .description("Counts the number of successful calls to syfo-tilgangskontroll - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_FAIL)
    .description("Counts the number of failed calls to syfo-tilgangskontroll - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN)
    .description("Counts the number of forbidden calls to syfo-tilgangskontroll - person")
    .register(METRICS_REGISTRY)

const val OPPFOLGINGSPLANLPS_FIRST_OVERSIKTHENDELSE_RETRY =
    "${METRICS_NS}_oppfolgingsplanlps_first_oversikthendelse_retry_count"
val COUNT_OPPFOLGINGSPLANLPS_FIRST_OVERSIKTHENDELSE_RETRY: Counter =
    Counter.builder(OPPFOLGINGSPLANLPS_FIRST_OVERSIKTHENDELSE_RETRY)
        .description("Counts the number of KOppfolgingsplanLPS generated new OversikthendelseRetry because BehandlendeEnhet was not found")
        .register(METRICS_REGISTRY)

const val OVERSIKTHENDELSE_RETRY_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SKIPPED =
    "${METRICS_NS}_oppfolgingsplanlps_skipped_retry_count"
val COUNT_OVERSIKTHENDELSE_RETRY_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SKIPPED: Counter =
    Counter.builder(OVERSIKTHENDELSE_RETRY_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SKIPPED)
        .description("Counts the number of Oversikthendelse with type OPPFOLGINGSPLANLPS_MOTTATT not sent due to reached try limit for")
        .register(METRICS_REGISTRY)

const val OVERSIKTHENDELSE_RETRY_FIRST = "${METRICS_NS}_oversikthendelse_retry_first_count"
val COUNT_OVERSIKTHENDELSE_RETRY_FIRST: Counter = Counter.builder(OVERSIKTHENDELSE_RETRY_FIRST)
    .description("Counts the number of OversikthendelseRetry with unchanged retryCount sent")
    .register(METRICS_REGISTRY)
const val OVERSIKTHENDELSE_RETRY_NEW = "${METRICS_NS}_oversikthendelse_retry_new_count"
val COUNT_OVERSIKTHENDELSE_RETRY_NEW: Counter = Counter.builder(OVERSIKTHENDELSE_RETRY_NEW)
    .description("Counts the number of OversikthendelseRetry with increased retryCount sent")
    .register(METRICS_REGISTRY)
const val OVERSIKTHENDELSE_RETRY_AGAIN = "${METRICS_NS}_oversikthendelse_retry_again_count"
val COUNT_OVERSIKTHENDELSE_RETRY_AGAIN: Counter = Counter.builder(OVERSIKTHENDELSE_RETRY_AGAIN)
    .description("Counts the number of OversikthendelseRetry with increased retryCount sent")
    .register(METRICS_REGISTRY)
