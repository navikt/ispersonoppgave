package no.nav.syfo.personoppgavehendelse

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.personoppgave.domain.PersonOppgave

class PublishPersonoppgavehendelseService(
    private val database: DatabaseInterface,
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    fun getUnpublishedOppgaver(): List<PersonOppgave> {
        return emptyList()
    }

    fun publish(personOppgave: PersonOppgave) {}
}
