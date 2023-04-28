package no.nav.syfo.personoppgave

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.toList
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.sql.Connection

const val queryGetPersonOppgaverByPublish =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE publish = ?
    ORDER BY id ASC
    LIMIT 100
    """

fun Connection.getPersonOppgaverByPublish(publish: Boolean): List<PPersonOppgave> {
    return prepareStatement(queryGetPersonOppgaverByPublish).use {
        it.setBoolean(1, publish)
        it.executeQuery().toList {
            toPPersonOppgave()
        }
    }
}

const val queryGetUbehandledePersonOppgaver =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE fnr = ?
      AND type = ?
      AND behandlet_tidspunkt IS NULL
      AND behandlet_veileder_ident IS NULL
    """

fun DatabaseInterface.getUbehandledePersonOppgaver(personIdent: PersonIdent, personOppgaveType: PersonOppgaveType): List<PPersonOppgave> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetUbehandledePersonOppgaver).use {
            it.setString(1, personIdent.value)
            it.setString(2, personOppgaveType.name)
            it.executeQuery().toList { toPPersonOppgave() }
        }
    }
}
