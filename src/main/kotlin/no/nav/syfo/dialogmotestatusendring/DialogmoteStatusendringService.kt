package no.nav.syfo.dialogmotestatusendring

import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.dialogmotestatusendring.kafka.*
import no.nav.syfo.personoppgave.behandleOppgave
import java.sql.Connection

fun processDialogmoteStatusendring(
    connection: Connection,
    statusendring: DialogmoteStatusendring,
) {
    log.info("Received statusendring of type ${statusendring.type} and uuid ${statusendring.dialogmoteUuid}")

    if (statusendring.type != DialogmoteStatusendringType.INNKALT) {
        val success = connection.behandleOppgave(
            statusendring.dialogmoteUuid,
            statusendring.veilederIdent,
        )

        if (success) {
            // TODO: add oppgavebehandlet of dialogmotesvar_behandlet to personoppgavehendelse topic
        } else {
            log.info("No personoppgave to update for ${statusendring.dialogmoteUuid}")
        }
    }
}
