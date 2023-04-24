package no.nav.syfo.meldingfrabehandler

import no.nav.syfo.meldingfrabehandler.domain.MeldingFraBehandler
import no.nav.syfo.meldingfrabehandler.kafka.log
import no.nav.syfo.personoppgave.*
import java.sql.Connection

fun processMeldingFraBehandler(
    meldingFraBehandler: MeldingFraBehandler,
    connection: Connection,
) {
    log.info("Received melding fra behandler! ${meldingFraBehandler.referanseUuid}")
    connection.createPersonOppgave(meldingFraBehandler)
}
