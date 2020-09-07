package no.nav.syfo.personoppgave

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.toPersonOppgave

class PersonOppgaveService(
    private val database: DatabaseInterface
) {
    fun getPersonOppgaveList(
        fnr: Fodselsnummer
    ): List<PersonOppgave> {
        return database.getPersonOppgaveList(fnr).map {
            it.toPersonOppgave()
        }
    }
}
