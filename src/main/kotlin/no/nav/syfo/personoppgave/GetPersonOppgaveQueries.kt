package no.nav.syfo.personoppgave

import no.nav.syfo.database.toList
import no.nav.syfo.personoppgave.domain.PPersonOppgave
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
