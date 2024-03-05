package no.nav.syfo.personoppgave

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.toList
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import java.sql.Connection
import java.util.*

const val queryGetPersonOppgaverByFnr =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE fnr = ?
    """

fun DatabaseInterface.getPersonOppgaver(personIdent: PersonIdent): List<PPersonOppgave> {
    return connection.use { connection ->
        connection.getPersonOppgaver(personIdent)
    }
}

fun Connection.getPersonOppgaver(personIdent: PersonIdent): List<PPersonOppgave> {
    return prepareStatement(queryGetPersonOppgaverByFnr).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList { toPPersonOppgave() }
    }
}

const val queryGetPersonOppgaverByUUID =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE uuid = ?
    """

fun DatabaseInterface.getPersonOppgaveByUuid(uuid: UUID): PPersonOppgave? {
    return connection.use { connection ->
        connection.prepareStatement(queryGetPersonOppgaverByUUID).use {
            it.setString(1, uuid.toString())
            it.executeQuery().toList { toPPersonOppgave() }.firstOrNull()
        }
    }
}

const val queryGetPersonOppgaverByReferanseUUID =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE referanse_uuid = ?
    """

fun Connection.getPersonOppgaverByReferanseUuid(referanseUuid: UUID): List<PPersonOppgave> {
    return prepareStatement(queryGetPersonOppgaverByReferanseUUID).use {
        it.setString(1, referanseUuid.toString())
        it.executeQuery().toList {
            toPPersonOppgave()
        }
    }
}

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
