package no.nav.syfo.personoppgave

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
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

    fun behandlePersonOppgave(
        personoppgave: PersonOppgave,
        veilederIdent: String,
    ) {
        when {
            personoppgave.shouldBePublishedDirectlyToKafka() -> behandle(personoppgave, veilederIdent)
            else -> behandleAndPrepareForCronjob(personoppgave, veilederIdent)
        }
    }

    fun behandlePersonOppgaver(
        personoppgaver: List<PersonOppgave>,
        veilederIdent: String,
    ) {
        val updatedPersonOppgaver = personoppgaver.map {
            it.behandle(veilederIdent)
        }
        val updatedRows = database.updatePersonoppgaver(
            personoppgaver = updatedPersonOppgaver,
        )
        LOG.info("Updated $updatedRows personoppgaver for personoppgavetype ${personoppgaver.first().type.name}")
    }

    fun getUbehandledePersonOppgaver(
        personIdent: PersonIdent,
        personOppgaveType: PersonOppgaveType,
    ): List<PersonOppgave> {
        return database.getUbehandledePersonOppgaver(
            personIdent = personIdent,
            personOppgaveType = personOppgaveType,
        ).map {
            it.toPersonOppgave()
        }
    }

    private fun behandleAndPrepareForCronjob(personOppgave: PersonOppgave, veilederIdent: String) {
        val behandletOppgave = personOppgave.behandleAndPrepareForCronjob(
            veilederIdent = veilederIdent,
        )
        database.connection.use { connection ->
            connection.updatePersonoppgave(behandletOppgave)
            connection.commit()
        }
    }

    private fun behandle(personoppgave: PersonOppgave, veilederIdent: String) {
        val behandletPersonOppgave = personoppgave.behandle(veilederIdent)

        database.updatePersonOppgaveBehandlet(
            updatedPersonoppgave = behandletPersonOppgave,
        )

        val hasNoOtherUbehandledeOppgaver = getUbehandledePersonOppgaver(
            personIdent = behandletPersonOppgave.personIdent,
            personOppgaveType = behandletPersonOppgave.type,
        ).isEmpty()

        val personoppgaveHendelseType = behandletPersonOppgave.toHendelseType()

        if (hasNoOtherUbehandledeOppgaver) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                personoppgaveHendelseType,
                behandletPersonOppgave.personIdent,
                behandletPersonOppgave.uuid,
            )
            LOG.info(
                "Sent Personoppgavehendelse, {}, {}",
                StructuredArguments.keyValue("type", personoppgaveHendelseType),
                StructuredArguments.keyValue("veilederident", veilederIdent)
            )
        } else {
            LOG.info(
                "No Personoppgavehendelse sent, there are other oppgaver of this type that still needs behandling, {}, {}",
                StructuredArguments.keyValue("type", personoppgaveHendelseType),
                StructuredArguments.keyValue("veilederident", veilederIdent)
            )
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PersonOppgaveService::class.java)
    }
}
