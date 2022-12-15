package no.nav.syfo.dialogmotesvar

import no.nav.syfo.dialogmotestatusendring.kafka.log
import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.metric.COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.util.toLocalDateTimeOslo
import java.sql.Connection
import java.time.LocalDate
import java.util.*

fun processDialogmotesvar(
    connection: Connection,
    dialogmotesvar: Dialogmotesvar,
    cutoffDate: LocalDate,
) {
    log.info("Received dialogmotesvar! ${dialogmotesvar.moteuuid}")
    if (isNotProcessable(dialogmotesvar, cutoffDate)) return

    val pPersonOppgave = connection.getPersonOppgaveByReferanseUuid(dialogmotesvar.moteuuid)

    if (pPersonOppgave == null) {
        val personoppgaveUuid = UUID.randomUUID()
        connection.createPersonOppgave(dialogmotesvar, personoppgaveUuid)
    } else {
        val oppgave = pPersonOppgave.toPersonOppgave()
        if (dialogmotesvar happenedAfter oppgave) {
            val updatedOppgave = oppgave.copy(
                behandletTidspunkt = null,
                behandletVeilederIdent = null,
                sistEndret = dialogmotesvar.svarReceivedAt.toLocalDateTimeOslo(),
                publish = true,
            )
            connection.updatePersonoppgave(updatedOppgave)
            COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED.increment()
        }
    }
}
fun storeDialogmotesvar(
    connection: Connection,
    dialogmotesvar: Dialogmotesvar,
) {
    connection.createMotesvar(dialogmotesvar)
}

fun isProcessable(dialogmotesvar: Dialogmotesvar, cutoffDate: LocalDate): Boolean {
    return dialogmotesvar.isRelevantToVeileder() && dialogmotesvar happenedAfter cutoffDate
}

fun isNotProcessable(dialogmotesvar: Dialogmotesvar, cutoffDate: LocalDate) = !isProcessable(dialogmotesvar, cutoffDate)
