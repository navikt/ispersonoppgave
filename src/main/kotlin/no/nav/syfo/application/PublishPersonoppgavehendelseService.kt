package no.nav.syfo.application

import no.nav.syfo.domain.PersonOppgave
import no.nav.syfo.domain.hasSameOppgaveTypeAs
import no.nav.syfo.domain.toHendelseType
import no.nav.syfo.infrastructure.database.queries.toPersonOppgaver
import no.nav.syfo.infrastructure.database.queries.getPersonOppgaver
import no.nav.syfo.infrastructure.database.queries.getPersonOppgaverByPublish
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.PersonoppgavehendelseProducer
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

class PublishPersonoppgavehendelseService(
    val database: DatabaseInterface,
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer,
    private val personOppgaveRepository: PersonOppgaveRepository,
) {

    fun getUnpublishedOppgaver(): List<PersonOppgave> {
        return database.connection.use {
            it.getPersonOppgaverByPublish(publish = true).toPersonOppgaver()
        }
    }

    private fun isNewest(personOppgave: PersonOppgave): Boolean {
        val personOppgaver = database.connection.use {
            it.getPersonOppgaver(personOppgave.personIdent).toPersonOppgaver()
        }

        val newestOppgave = personOppgaver
            .filter { it hasSameOppgaveTypeAs personOppgave }
            .maxBy { it.sistEndret }

        return personOppgave.uuid == newestOppgave.uuid
    }

    fun publish(personOppgave: PersonOppgave) {
        val publish = isNewest(personOppgave)
        if (publish) {
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = personOppgave.toHendelseType(),
                personIdent = personOppgave.personIdent,
                personoppgaveId = personOppgave.uuid,
            )
        } else {
            log.info("Do not publish PersonOppgave with uuid ${personOppgave.uuid} because newer oppgave with same hendelseType exists")
        }
        val updatedPersonOppgave = personOppgave.copy(
            publish = false,
            publishedAt = if (publish) OffsetDateTime.now() else null,
        )

        personOppgaveRepository.updatePersonoppgaveBehandlet(personOppgave = updatedPersonOppgave)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishPersonoppgavehendelseService::class.java)
    }
}
