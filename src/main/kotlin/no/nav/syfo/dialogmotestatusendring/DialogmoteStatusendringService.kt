package no.nav.syfo.dialogmotestatusendring

import no.nav.syfo.dialogmotestatusendring.domain.*
import no.nav.syfo.dialogmotestatusendring.kafka.log
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import java.sql.Connection
import java.util.*

fun processDialogmoteStatusendring(
    connection: Connection,
    statusendring: DialogmoteStatusendring,
) {
    log.info("Received statusendring of type ${statusendring.type} and uuid ${statusendring.dialogmoteUuid}")

    val ppersonOppgave = connection.getPersonOppgaveByReferanseUuid(statusendring.dialogmoteUuid)
    if (ppersonOppgave == null) {
        val personoppgaveUuid = UUID.randomUUID()
        connection.createBehandletPersonoppgave(statusendring, personoppgaveUuid)
    } else {
        val personOppgave = ppersonOppgave.toPersonOppgave()
        if (statusendring happenedAfter personOppgave || statusendring.didFinishDialogmote()) {
            val updatedOppgave = personOppgave.copy(
                behandletTidspunkt = statusendring.endringTidspunkt.toLocalDateTimeOslo(),
                behandletVeilederIdent = statusendring.veilederIdent,
                sistEndret = statusendring.endringTidspunkt.toLocalDateTimeOslo(),
                publish = true,
            )
            connection.updatePersonoppgave(updatedOppgave)
        }
    }
}

fun storeDialogmoteStatusendring(
    connection: Connection,
    statusendring: DialogmoteStatusendring,
) {
    connection.createDialogmoteStatusendring(statusendring)
}
