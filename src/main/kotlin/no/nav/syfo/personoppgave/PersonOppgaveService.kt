package no.nav.syfo.personoppgave

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import org.slf4j.LoggerFactory
import java.util.*

class PersonOppgaveService(
    private val database: DatabaseInterface,
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    fun getPersonOppgaveList(
        personIdent: PersonIdent
    ): List<PersonOppgave> {
        return database.getPersonOppgaveList(personIdent).map {
            it.toPersonOppgave()
        }
    }

    fun getPersonOppgave(
        uuid: UUID,
    ): PersonOppgave? {
        val oppgaveList = database.getPersonOppgaveList(uuid)
        return if (oppgaveList.isEmpty()) {
            null
        } else {
            oppgaveList.first().toPersonOppgave()
        }
    }

    suspend fun behandlePersonOppgave(
        personoppgave: PersonOppgave,
        veilederIdent: String,
        callId: String,
    ) {
        val personFnr = personoppgave.personIdent

        val isOnePersonOppgaveUbehandlet = getPersonOppgaveList(personFnr)
            .filter { it.behandletTidspunkt == null && it.type == PersonOppgaveType.OPPFOLGINGSPLANLPS }
            .size == 1

        if (isOnePersonOppgaveUbehandlet) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET,
                personFnr,
                personoppgave.uuid,
            )
            LOG.info(
                "Sent Personoppgavehendelse, {}, {}",
                StructuredArguments.keyValue("type", PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET),
                StructuredArguments.keyValue("veilederident", veilederIdent)
            )
        } else {
            LOG.info(
                "No Personoppgavehendelse sent, isOnePersonOppgaveUbehandlet=false, {}, {}",
                StructuredArguments.keyValue("type", PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET),
                StructuredArguments.keyValue("veilederident", veilederIdent)
            )
        }
        database.updatePersonOppgaveBehandlet(
            personoppgave.uuid,
            veilederIdent
        )
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PersonOppgaveService::class.java)
    }
}
