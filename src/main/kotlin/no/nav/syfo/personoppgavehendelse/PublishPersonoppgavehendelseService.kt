package no.nav.syfo.personoppgavehendelse

import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.OffsetDateTime

class PublishPersonoppgavehendelseService(
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    private val log = LoggerFactory.getLogger(PublishPersonoppgavehendelseService::class.java)

    fun getUnpublishedOppgaver(connection: Connection): List<PersonOppgave> {
        return connection.getPersonOppgaverByPublish(publish = true).toPersonOppgaver()
    }

    private fun isNewest(connection: Connection, personOppgave: PersonOppgave): Boolean {
        val personOppgaver = connection.getPersonOppgaver(personOppgave.personIdent).toPersonOppgaver()

        val newestOppgave = personOppgaver
            .filter { it hasSameOppgaveTypeAs personOppgave }
            .maxBy { it.sistEndret }

        return personOppgave.uuid == newestOppgave.uuid
    }

    fun publish(connection: Connection, personOppgave: PersonOppgave) { // TODO: add db to class instead of sending in connections
        val updatedPersonOppgave: PersonOppgave

        if (isNewest(connection, personOppgave)) {
            val hendelsetype = personOppgave.toHendelseType()
            personoppgavehendelseProducer.sendPersonoppgavehendelse(
                hendelsetype = hendelsetype,
                personIdent = personOppgave.personIdent,
                personoppgaveId = personOppgave.uuid,
            )
            updatedPersonOppgave = personOppgave.copy(
                publish = false,
                publishedAt = OffsetDateTime.now(),
            )
        } else {
            log.info("Do not publish PersonOppgave with uuid ${personOppgave.uuid} because oppgave from later meeting exists")
            updatedPersonOppgave = personOppgave.copy(publish = false)
        }
        connection.updatePersonoppgave(updatedPersonOppgave)
    }
}
