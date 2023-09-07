package no.nav.syfo.behandlerdialog

import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_AVVIST_MELDING_MOTTATT
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AvvistMeldingService(
    private val database: DatabaseInterface,
    private val personOppgaveService: PersonOppgaveService,
) {
    fun processAvvistMelding(recordPairs: List<Pair<String, Melding>>) {
        database.connection.use { connection ->
            recordPairs.forEach { record ->
                val melding = record.second
                log.info("Received avvistMelding with key=${record.first}, uuid=${melding.referanseUuid} and parentRef=${melding.parentRef}")

                if (melding.referanseUuid.toString() == "f7bfd93e-3807-448d-ad0b-07fb51855ad2") {
                    return@forEach
                }

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
            }
            connection.commit()
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
