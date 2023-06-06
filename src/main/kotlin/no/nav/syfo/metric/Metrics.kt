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

const val PERSONOPPGAVEHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT =
    "${METRICS_NS}_personoppgavehendelse_oppfolgingsplanlps_bistand_mottatt_sent_count"
val COUNT_PERSONOPPGAVEHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT: Counter =
    Counter.builder(PERSONOPPGAVEHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT)
        .description("Counts the number of personoppgavehendelse with PersonoppgavehendelseType OPPFOLGINGSPLANLPS_BISTAND_MOTTATT created from a KOppfolgingsplanLPS")
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

const val DIALOGMOTESVAR_OPPGAVE_UPDATED = "${METRICS_NS}_dialogmotesvar_oppgave_updated_count"
val COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED: Counter =
    Counter.builder(DIALOGMOTESVAR_OPPGAVE_UPDATED)
        .description("Counts the number of PERSON_OPPGAVE updated from a KDialogmotesvar")
        .register(METRICS_REGISTRY)

const val PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT =
    "${METRICS_NS}_personoppgavehendelse_dialogmelding_svar_mottatt_count"
val COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT: Counter =
    Counter.builder(PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT)
        .description("Counts the number of personoppgavehendelse with PersonoppgavehendelseType BEHANDLERDIALOG_SVAR_MOTTATT created from a KMeldingDTO")
        .register(METRICS_REGISTRY)

const val PERSONOPPGAVEHENDELSE_UBESVART_MELDING_MOTTATT =
    "${METRICS_NS}_personoppgavehendelse_ubesvart_melding_mottatt_count"
val COUNT_PERSONOPPGAVEHENDELSE_UBESVART_MELDING_MOTTATT: Counter =
    Counter.builder(PERSONOPPGAVEHENDELSE_UBESVART_MELDING_MOTTATT)
        .description("Counts the number of personoppgavehendelse with PersonoppgavehendelseType BEHANDLERDIALOG_UBESVART_MELDING_MOTTATT created from a KMeldingDTO")
        .register(METRICS_REGISTRY)
