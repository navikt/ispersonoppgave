package no.nav.syfo.dialogmotestatusendring

import no.nav.syfo.dialogmotestatusendring.domain.*
import no.nav.syfo.dialogmotestatusendring.kafka.log
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.util.toLocalDateTimeOslo
import java.sql.Connection
import java.util.*

fun processDialogmoteStatusendring(
    connection: Connection,
    statusendring: DialogmoteStatusendring,
) {
    log.info("Received statusendring of type ${statusendring.type} and uuid ${statusendring.dialogmoteUuid}")

    val personOppgave = connection
        .getPersonOppgaverByReferanseUuid(statusendring.dialogmoteUuid)
        .map { it.toPersonOppgave() }
        .firstOrNull { it.type == PersonOppgaveType.DIALOGMOTESVAR && it.isUBehandlet() }

    if (personOppgave == null) {
        val personoppgaveUuid = UUID.randomUUID()
        connection.createBehandletPersonoppgave(statusendring, personoppgaveUuid)
    } else {
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
