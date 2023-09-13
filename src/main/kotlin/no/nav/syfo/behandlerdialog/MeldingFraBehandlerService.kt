package no.nav.syfo.behandlerdialog

import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.util.Constants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

class MeldingFraBehandlerService(
    private val database: DatabaseInterface,
    private val personOppgaveService: PersonOppgaveService,
) {

    internal fun processMeldingerFraBehandler(recordPairs: List<Pair<String, Melding>>) {
        val existingOppgaverBehandlet = mutableListOf<PersonOppgave>()
        database.connection.use { connection ->
            recordPairs.forEach { record ->
                val melding = record.second
                KafkaMeldingFraBehandler.log.info("Received meldingFraBehandler with key=$record.first, uuid=${melding.referanseUuid} and parentRef=${melding.parentRef}")

                processMeldingFraBehandler(
                    melding = melding,
                    connection = connection,
                )
                handleExistingUbesvartMeldingOppgave(
                    melding = melding,
                    connection = connection,
                )?.also {
                    existingOppgaverBehandlet.add(it)
                }
                COUNT_PERSONOPPGAVEHENDELSE_DIALOGMELDING_SVAR_MOTTATT.increment()
            }
            connection.commit()
        }
        existingOppgaverBehandlet.forEach {
            personOppgaveService.publishIfAllOppgaverBehandlet(
                behandletPersonOppgave = it,
                veilederIdent = Constants.SYSTEM_VEILEDER_IDENT,
            )
        }
    }

    private fun processMeldingFraBehandler(
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

    private fun handleExistingUbesvartMeldingOppgave(
        melding: Melding,
        connection: Connection,
    ): PersonOppgave? =
        if (melding.parentRef != null) {
            val existingOppgave = connection
                .getPersonOppgaverByReferanseUuid(melding.parentRef)
                .map { it.toPersonOppgave() }
                .firstOrNull { it.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART && it.isUBehandlet() }

            if (existingOppgave != null) {
                log.info("Received svar on ubesvart melding for oppgave with uuid ${existingOppgave.uuid}, behandles automatically by system")
                personOppgaveService.markOppgaveAsBehandletBySystem(
                    personOppgave = existingOppgave,
                    connection = connection,
                )
            } else null
        } else null

    companion object {
        val log: Logger = LoggerFactory.getLogger(MeldingFraBehandlerService::class.java)
    }
}
