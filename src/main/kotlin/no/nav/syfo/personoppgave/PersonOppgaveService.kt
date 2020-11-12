package no.nav.syfo.personoppgave

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.personoppgave.domain.*
import org.slf4j.LoggerFactory
import java.util.*

class PersonOppgaveService(
    private val database: DatabaseInterface,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val oversikthendelseProducer: OversikthendelseProducer
) {
    fun getPersonOppgaveList(
        fnr: Fodselsnummer
    ): List<PersonOppgave> {
        return database.getPersonOppgaveList(fnr).map {
            it.toPersonOppgave()
        }
    }

    fun getPersonOppgave(
        uuid: UUID
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
        callId: String
    ) {
        val personFnr = personoppgave.fnr
        val behandlendeEnhet = behandlendeEnhetClient.getEnhet(personFnr, callId)
            ?: throw BehandlePersonOppgaveFailedException("Veileder $veilederIdent failed to get BehandleEnhet for PersonIdent Fodselsnummer")

        val isOnePersonOppgaveUbehandlet = getPersonOppgaveList(personFnr)
            .filter { it.behandletTidspunkt == null && it.type == PersonOppgaveType.OPPFOLGINGSPLANLPS }
            .size == 1

        if (isOnePersonOppgaveUbehandlet) {
            oversikthendelseProducer.sendOversikthendelse(
                personFnr,
                behandlendeEnhet,
                OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET,
                callId
            )
            LOG.info(
                "Sent Oversikthendelse, {}, {}",
                StructuredArguments.keyValue("type", OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET),
                StructuredArguments.keyValue("veilederident", veilederIdent)
            )
        } else {
            LOG.info(
                "No Oversikthendelse sent, isOnePersonOppgaveUbehandlet=false, {}, {}",
                StructuredArguments.keyValue("type", OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET),
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
