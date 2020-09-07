package no.nav.syfo.personoppgave

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import java.util.*

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
        uuid: UUID,
        veilederIdent: String
    ) {
        database.updatePersonOppgaveBehandlet(
            uuid,
            veilederIdent
        )
    }
}
