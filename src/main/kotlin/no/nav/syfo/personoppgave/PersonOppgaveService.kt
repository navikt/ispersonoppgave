package no.nav.syfo.personoppgave

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.util.toLocalDateTimeOslo
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
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

    fun behandlePersonOppgave(
        personoppgave: PersonOppgave,
        veilederIdent: String,
    ) {
        when (personoppgave.type) {
            PersonOppgaveType.OPPFOLGINGSPLANLPS -> behandleLps(personoppgave, veilederIdent)
            else -> behandle(personoppgave, veilederIdent)
        }
    }

    fun behandle(personOppgave: PersonOppgave, veilederIdent: String) {
        val now = OffsetDateTime.now().toLocalDateTimeOslo()
        val updatedOppgave = personOppgave.copy(
            behandletTidspunkt = now,
            behandletVeilederIdent = veilederIdent,
            sistEndret = now,
            publish = true,
        )
        database.connection.use { connection ->
            connection.updatePersonoppgave(updatedOppgave)
            connection.commit()
        }
    }

    fun behandleLps(personoppgave: PersonOppgave, veilederIdent: String) {
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
