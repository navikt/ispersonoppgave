package no.nav.syfo.behandlerdialog

import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import java.sql.Connection

class AvvistMeldingService(
    private val personOppgaveService: PersonOppgaveService,
) {
    fun processAvvistMelding(
        melding: Melding,
        connection: Connection,
    ) {
        val oppgaveUuid = connection.createPersonOppgave(
            melding = melding,
            personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST,
        )
        personOppgaveService.publishPersonoppgaveHendelse(
            personoppgavehendelseType = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_MOTTATT,
            personIdent = melding.personIdent,
            personoppgaveUUID = oppgaveUuid,
        )
    }
}
