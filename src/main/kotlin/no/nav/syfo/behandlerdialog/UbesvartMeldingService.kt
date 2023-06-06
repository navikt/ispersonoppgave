package no.nav.syfo.behandlerdialog

import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.sql.Connection

fun processUbesvartMelding(
    melding: Melding,
    connection: Connection,
) {
    KafkaUbesvartMelding.log.info("Received ubesvart melding with uuid: ${melding.referanseUuid}")
    connection.createPersonOppgave(
        melding = melding,
        personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_UBESVART_MELDING,
    )
}
