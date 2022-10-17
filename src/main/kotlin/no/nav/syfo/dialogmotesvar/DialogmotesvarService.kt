package no.nav.syfo.dialogmotesvar

import no.nav.syfo.dialogmotestatusendring.kafka.log
import no.nav.syfo.dialogmotesvar.domain.Dialogmotesvar
import java.sql.Connection

fun processDialogmotesvar(
    connection: Connection,
    dialogmotesvar: Dialogmotesvar,
) {
    log.info("Received dialogmotesvar! ${dialogmotesvar.moteuuid}")
}
