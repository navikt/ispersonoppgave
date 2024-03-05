package no.nav.syfo.personoppgave

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.sql.Connection

interface IPersonOppgaveRepository {
    fun getUbehandledePersonoppgaver(personIdent: PersonIdent, type: PersonOppgaveType, connection: Connection? = null): List<PersonOppgave>
    fun createPersonoppgave(personOppgave: PersonOppgave, connection: Connection? = null)
}
