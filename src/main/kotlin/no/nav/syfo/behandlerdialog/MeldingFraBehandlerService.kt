package no.nav.syfo.behandlerdialog

import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import no.nav.syfo.personoppgave.updatePersonOppgaveBehandlet
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.util.Constants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

class MeldingFraBehandlerService(
    private val database: DatabaseInterface,
    private val personOppgaveService: PersonOppgaveService,
) {

    internal fun processMeldingFraBehandler(
        melding: Melding,
        connection: Connection,
    ) {
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

    internal fun handleExistingUbesvartMeldingOppgave(melding: Melding) {
        if (melding.parentRef != null) {
            database.connection.use { connection ->
                val existingOppgave = connection
                    .getPersonOppgaverByReferanseUuid(melding.parentRef)
                    .map { it.toPersonOppgave() }
                    .firstOrNull { it.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART && it.isUBehandlet() }

                if (existingOppgave != null) {
                    log.info("Received svar on ubesvart melding for oppgave with uuid ${existingOppgave.uuid}, behandles automatically by system")
                    markOppgaveAsBehandletBySystem(
                        meldingUbesvartOppgave = existingOppgave,
                    )
                }
            }
        }
    }

    private fun markOppgaveAsBehandletBySystem(meldingUbesvartOppgave: PersonOppgave) {
        val veilederIdent = Constants.SYSTEM_VEILEDER_IDENT
        val behandletPersonoppgave = meldingUbesvartOppgave.behandle(
            veilederIdent = veilederIdent,
        )
        database.updatePersonOppgaveBehandlet(behandletPersonoppgave)

        personOppgaveService.publishIfAllOppgaverBehandlet(
            behandletPersonOppgave = behandletPersonoppgave,
            veilederIdent = veilederIdent,
        )
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(MeldingFraBehandlerService::class.java)
    }
}
