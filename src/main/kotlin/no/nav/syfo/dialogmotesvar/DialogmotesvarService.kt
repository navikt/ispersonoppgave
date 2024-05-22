package no.nav.syfo.dialogmotesvar

import io.micrometer.core.instrument.Counter
import no.nav.syfo.dialogmotestatusendring.domain.didFinishDialogmote
import no.nav.syfo.dialogmotestatusendring.getDialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.kafka.log
import no.nav.syfo.dialogmotesvar.Metrics.COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED
import no.nav.syfo.dialogmotesvar.Metrics.COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER
import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.util.toLocalDateTimeOslo
import java.sql.Connection
import java.time.LocalDate

fun processDialogmotesvar(
    connection: Connection,
    dialogmotesvar: Dialogmotesvar,
    cutoffDate: LocalDate,
) {
    log.info("Received dialogmotesvar! ${dialogmotesvar.moteuuid}")
    if (dialogmotesvar.isIrrelevant(cutoffDate) || isDialogmoteClosed(connection, dialogmotesvar)) return

    val personOppgave = connection
        .getPersonOppgaverByReferanseUuid(dialogmotesvar.moteuuid)
        .map { it.toPersonOppgave() }
        .firstOrNull { it.type == PersonOppgaveType.DIALOGMOTESVAR }

    if (personOppgave == null) {
        connection.createPersonOppgave(dialogmotesvar)
    } else {
        if (dialogmotesvar happenedAfter personOppgave) {
            val updatedOppgave = personOppgave.copy(
                behandletTidspunkt = null,
                behandletVeilederIdent = null,
                sistEndret = dialogmotesvar.svarReceivedAt.toLocalDateTimeOslo(),
                publish = true,
            )
            connection.updatePersonoppgaveSetBehandlet(updatedOppgave)
            COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED.increment()
            if (dialogmotesvar.svarType == DialogmoteSvartype.KOMMER) {
                COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER.increment()
            }
        }
    }
}

fun storeDialogmotesvar(
    connection: Connection,
    dialogmotesvar: Dialogmotesvar,
) {
    connection.createDialogmotesvar(dialogmotesvar)
}

fun isDialogmoteClosed(connection: Connection, dialogmotesvar: Dialogmotesvar): Boolean {
    val dialogmoteStatusendring = connection.getDialogmoteStatusendring(dialogmotesvar.moteuuid)
    val latestStatusEndring = dialogmoteStatusendring.maxByOrNull { it.endringTidspunkt }

    return latestStatusEndring != null && latestStatusEndring.didFinishDialogmote()
}

private object Metrics {
    const val DIALOGMOTESVAR_OPPGAVE_UPDATED = "${METRICS_NS}_dialogmotesvar_oppgave_updated_count"
    val COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED: Counter =
        Counter.builder(DIALOGMOTESVAR_OPPGAVE_UPDATED)
            .description("Counts the number of PERSON_OPPGAVE updated from a KDialogmotesvar")
            .register(METRICS_REGISTRY)
    const val DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER = "${METRICS_NS}_dialogmotesvar_oppgave_updated_kommer_count"
    val COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER: Counter =
        Counter.builder(DIALOGMOTESVAR_OPPGAVE_UPDATED_KOMMER)
            .description("Counts the number of PERSON_OPPGAVE updated from a KDialogmotesvar KOMMER")
            .register(METRICS_REGISTRY)
}
