package no.nav.syfo.behandlerdialog

import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.personoppgave.updatePersonOppgaveBehandlet
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.util.Constants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

class MeldingFraBehandlerService(
    private val personOppgaveService: PersonOppgaveService,
) {

    fun processMeldingFraBehandler(
        melding: Melding,
        connection: Connection,
    ) {
        if (melding.parentRef != null) {
            val existingOppgave = connection
                .getPersonOppgaveByReferanseUuid(melding.parentRef)
                ?.toPersonOppgave()

            if (existingOppgave != null &&
                existingOppgave.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART &&
                existingOppgave.isUBehandlet()
            ) {
                log.info("Received svar on ubesvart melding for oppgave with uuid ${existingOppgave.uuid}, behandles automatically by system")
                markOppgaveAsBehandletBySystem(
                    meldingUbesvartOppgave = existingOppgave,
                    connection = connection,
                )
            }
        }
        val oppgaveUuid = connection.createPersonOppgave(
            melding = melding,
            personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_SVAR,
        )

        personOppgaveService.publishPersonoppgaveHendelse(
            personoppgavehendelseType = PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT,
            personIdent = melding.personIdent,
            personoppgaveUUID = oppgaveUuid,
        )
    }

    private fun markOppgaveAsBehandletBySystem(meldingUbesvartOppgave: PersonOppgave, connection: Connection) {
        val veilederIdent = Constants.SYSTEM_VEILEDER_IDENT
        val behandletPersonoppgave = meldingUbesvartOppgave.behandle(
            veilederIdent = veilederIdent,
        )
        connection.updatePersonOppgaveBehandlet(behandletPersonoppgave)

        personOppgaveService.publishIfAlleOppgaverBehandlet(
            behandletPersonOppgave = behandletPersonoppgave,
            veilederIdent = veilederIdent,
        )
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(MeldingFraBehandlerService::class.java)
    }
}
