package no.nav.syfo.personoppgave

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.util.Constants
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.*

class PersonOppgaveService(
    private val database: DatabaseInterface,
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer,
    private val personoppgaveRepository: PersonOppgaveRepository,
) {
    fun getPersonOppgaveList(
        personIdent: PersonIdent
    ): List<PersonOppgave> {
        return database.getPersonOppgaver(personIdent).map {
            it.toPersonOppgave()
        }
    }

    fun getPersonOppgave(
        uuid: UUID,
    ) = database.getPersonOppgaveByUuid(uuid)?.toPersonOppgave()

    fun behandlePersonOppgave(
        personoppgave: PersonOppgave,
        veilederIdent: String,
    ) {
        when {
            personoppgave.shouldPublishOppgaveHendelseNow() -> behandleAndPublishOppgaveHendelse(personoppgave, veilederIdent)
            else -> behandleAndReadyForPublish(personoppgave, veilederIdent)
        }
    }

    fun behandlePersonOppgaver(
        personoppgaver: List<PersonOppgave>,
        veilederIdent: String,
    ) {
        val updatedPersonOppgaver = personoppgaver.map {
            it.behandle(veilederIdent)
        }
        val updatedRows = database.updatePersonoppgaverBehandlet(
            updatedPersonoppgaver = updatedPersonOppgaver,
        )
        LOG.info("Updated $updatedRows personoppgaver for personoppgavetype ${personoppgaver.first().type.name}")

        publishIfAllOppgaverBehandlet(
            behandletPersonOppgave = updatedPersonOppgaver.first(),
            veilederIdent = veilederIdent,
        )
    }

    fun getUbehandledePersonOppgaver(
        personIdent: PersonIdent,
        personOppgaveType: PersonOppgaveType,
    ): List<PersonOppgave> {
        return personoppgaveRepository.getUbehandledePersonoppgaver(
            personIdent = personIdent,
            type = personOppgaveType,
        )
    }

    private fun behandleAndReadyForPublish(personOppgave: PersonOppgave, veilederIdent: String) {
        val behandletOppgave = personOppgave.behandleAndReadyForPublish(
            veilederIdent = veilederIdent,
        )
        personoppgaveRepository.updatePersonoppgaveBehandlet(personOppgave = behandletOppgave)
    }

    private fun behandleAndPublishOppgaveHendelse(personoppgave: PersonOppgave, veilederIdent: String) {
        val behandletPersonOppgave = personoppgave.behandle(veilederIdent)

        database.updatePersonOppgaveBehandlet(
            updatedPersonoppgave = behandletPersonOppgave,
        )

        publishIfAllOppgaverBehandlet(
            behandletPersonOppgave = behandletPersonOppgave,
            veilederIdent = veilederIdent,
        )
    }

    fun publishIfAllOppgaverBehandlet(behandletPersonOppgave: PersonOppgave, veilederIdent: String) {
        val hasNoOtherUbehandledeOppgaverOfSameType = getUbehandledePersonOppgaver(
            personIdent = behandletPersonOppgave.personIdent,
            personOppgaveType = behandletPersonOppgave.type,
        ).isEmpty()

        val personoppgavehendelseType = behandletPersonOppgave.toHendelseType()

        if (hasNoOtherUbehandledeOppgaverOfSameType) {
            publishPersonoppgaveHendelse(
                personoppgavehendelseType = personoppgavehendelseType,
                personIdent = behandletPersonOppgave.personIdent,
                personoppgaveUUID = behandletPersonOppgave.uuid,
            )
            LOG.info(
                "Sent Personoppgavehendelse, {}, {}",
                StructuredArguments.keyValue("type", personoppgavehendelseType),
                StructuredArguments.keyValue("veilederident", veilederIdent)
            )
        } else {
            LOG.info(
                "No Personoppgavehendelse sent, there are other oppgaver of this type that still needs behandling, {}, {}",
                StructuredArguments.keyValue("type", personoppgavehendelseType),
                StructuredArguments.keyValue("veilederident", veilederIdent)
            )
        }
    }

    fun publishPersonoppgaveHendelse(
        personoppgavehendelseType: PersonoppgavehendelseType,
        personIdent: PersonIdent,
        personoppgaveUUID: UUID,
    ) {
        personoppgavehendelseProducer.sendPersonoppgavehendelse(
            personoppgavehendelseType,
            personIdent,
            personoppgaveUUID,
        )
    }

    fun markOppgaveAsBehandletBySystem(
        personOppgave: PersonOppgave,
        connection: Connection,
    ): PersonOppgave {
        val behandletPersonoppgave = personOppgave.behandle(
            veilederIdent = Constants.SYSTEM_VEILEDER_IDENT,
        )
        connection.updatePersonOppgaveBehandlet(behandletPersonoppgave)
        return behandletPersonoppgave
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PersonOppgaveService::class.java)
    }
}
