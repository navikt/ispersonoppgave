package no.nav.syfo.dialogmotesvar

import no.nav.syfo.dialogmotestatusendring.domain.didFinishDialogmote
import no.nav.syfo.dialogmotestatusendring.getDialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.kafka.log
import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.metric.COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED
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
    if (isIrrelevantDialogmotesvar(connection, dialogmotesvar, cutoffDate)) return

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

fun isIrrelevantDialogmotesvar(
    connection: Connection,
    dialogmotesvar: Dialogmotesvar,
    cutoffDate: LocalDate
): Boolean {
    return dialogmotesvar.svarType == DialogmoteSvartype.KOMMER ||
        !(dialogmotesvar happenedAfter cutoffDate) ||
        isDialogmoteClosed(connection, dialogmotesvar)
}
