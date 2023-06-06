package no.nav.syfo.behandlerdialog

import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.sql.Connection

fun processMeldingFraBehandler(
    melding: Melding,
    connection: Connection,
) {
    KafkaMeldingFraBehandler.log.info("Received melding fra behandler with uuid: ${melding.referanseUuid}")
    connection.createPersonOppgave(
        melding = melding,
        personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_SVAR,
    )
}
