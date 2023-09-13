package no.nav.syfo.behandlerdialog

import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_AVVIST_MELDING_MOTTATT
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.util.Constants
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AvvistMeldingService(
    private val database: DatabaseInterface,
    private val personOppgaveService: PersonOppgaveService,
) {
    fun processAvvistMelding(recordPairs: List<Pair<String, Melding>>) {
        val existingOppgaverBehandlet = mutableListOf<PersonOppgave>()
        database.connection.use { connection ->
            recordPairs.forEach { record ->
                val melding = record.second
                log.info("Received avvistMelding with key=${record.first}, uuid=${melding.referanseUuid} and parentRef=${melding.parentRef}")

                val oppgaveUuid = connection.createPersonOppgave(
                    melding = melding,
                    personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST,
                )

                personOppgaveService.publishPersonoppgaveHendelse(
                    personoppgavehendelseType = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_MOTTATT,
                    personIdent = melding.personIdent,
                    personoppgaveUUID = oppgaveUuid,
                )

                COUNT_PERSONOPPGAVEHENDELSE_AVVIST_MELDING_MOTTATT.increment()

                val existingOppgave = connection
                    .getPersonOppgaverByReferanseUuid(melding.referanseUuid)
                    .map { it.toPersonOppgave() }
                    .firstOrNull { it.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART && it.isUBehandlet() }

                if (existingOppgave != null) {
                    log.info("Received avvist melding for oppgave with uuid ${existingOppgave.uuid}, behandles automatically by system")
                    val behandletOppgave = personOppgaveService.markOppgaveAsBehandletBySystem(
                        personOppgave = existingOppgave,
                        connection = connection,
                    )
                    existingOppgaverBehandlet.add(behandletOppgave)
                }
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

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
