package no.nav.syfo.identhendelse.database

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PPersonOppgave

const val queryUpdatePersonOppgave =
    """
        UPDATE PERSON_OPPGAVE
        SET fnr = ?
        WHERE fnr = ?
    """

fun DatabaseInterface.updatePersonOppgave(nyPersonident: PersonIdent, personOppgaverWithOldIdent: List<PPersonOppgave>): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdatePersonOppgave).use {
            personOppgaverWithOldIdent.forEach { personOppgave ->
                it.setString(1, nyPersonident.value)
                it.setString(2, personOppgave.fnr)
                it.executeUpdate()
                updatedRows++
            }
        }
        connection.commit()
    }
    return updatedRows
}
