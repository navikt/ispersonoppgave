package no.nav.syfo.dialogmotesvar

import no.nav.syfo.dialogmotestatusendring.kafka.log
import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.metric.COUNT_DIALOGMOTESVAR_OPPGAVE_UPDATED
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.util.toLocalDateTimeOslo
import java.sql.Connection
import java.util.*

fun processDialogmotesvar(
    connection: Connection,
    dialogmotesvar: Dialogmotesvar,
) {
    log.info("Received dialogmotesvar! ${dialogmotesvar.moteuuid}")
    if (dialogmotesvar.isNotRelevantToVeileder()) return

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
