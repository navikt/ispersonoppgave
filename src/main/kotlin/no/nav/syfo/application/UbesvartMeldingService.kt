package no.nav.syfo.application

import no.nav.syfo.domain.Melding
import no.nav.syfo.infrastructure.database.queries.createPersonOppgave
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.domain.PersonoppgavehendelseType
import java.sql.Connection

class UbesvartMeldingService(
    private val personOppgaveService: PersonOppgaveService,
) {
    fun processUbesvartMelding(
        melding: Melding,
        connection: Connection,
    ) {
        val oppgaveUuid = connection.createPersonOppgave(
            melding = melding,
            personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART,
        )

        personOppgaveService.publishPersonoppgaveHendelse(
            personoppgavehendelseType = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_MOTTATT,
            personIdent = melding.personIdent,
            personoppgaveUUID = oppgaveUuid,
        )
    }
}
