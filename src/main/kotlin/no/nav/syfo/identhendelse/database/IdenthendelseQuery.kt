package no.nav.syfo.identhendelse.database

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.toList
import no.nav.syfo.domain.PersonIdent
import java.sql.Connection
import java.sql.PreparedStatement

const val queryUpdatePersonOppgave =
    """
        UPDATE PERSON_OPPGAVE
        SET fnr = ?
        WHERE fnr = ?
    """

fun Connection.updatePersonOppgave(
    nyPersonident: PersonIdent,
    inactiveIdenter: List<PersonIdent>,
    commit: Boolean = false,
): Int {
    return this.updateIdent(
        query = queryUpdatePersonOppgave,
        nyPersonident = nyPersonident,
        inactiveIdenter = inactiveIdenter,
        commit = commit,
    )
}

const val queryUpdateMotesvar =
    """
        UPDATE motesvar
        SET arbeidstaker_ident = ?
        WHERE arbeidstaker_ident = ?
    """

fun Connection.updateMotesvar(
    nyPersonident: PersonIdent,
    inactiveIdenter: List<PersonIdent>,
    commit: Boolean = false,
): Int {
    return this.updateIdent(
        query = queryUpdateMotesvar,
        nyPersonident = nyPersonident,
        inactiveIdenter = inactiveIdenter,
        commit = commit,
    )
}

const val queryUpdateStatusendring =
    """
        UPDATE dialogmote_statusendring
        SET arbeidstaker_ident = ?
        WHERE arbeidstaker_ident = ?
    """

fun Connection.updateDialogmoteStatusendring(
    nyPersonident: PersonIdent,
    inactiveIdenter: List<PersonIdent>,
    commit: Boolean = false,
): Int {
    return this.updateIdent(
        query = queryUpdateStatusendring,
        nyPersonident = nyPersonident,
        inactiveIdenter = inactiveIdenter,
        commit = commit,
    )
}

private fun Connection.updateIdent(
    query: String,
    nyPersonident: PersonIdent,
    inactiveIdenter: List<PersonIdent>,
    commit: Boolean = false,
): Int {
    var updatedRows = 0
    this.prepareStatement(query).use {
        inactiveIdenter.forEach { inactiveIdent ->
            it.setString(1, nyPersonident.value)
            it.setString(2, inactiveIdent.value)
            updatedRows += it.executeUpdate()
        }
    }
    if (commit) {
        this.commit()
    }
    return updatedRows
}

const val queryGetIdentCount =
    """
        SELECT COUNT(*)
        FROM (
            SELECT arbeidstaker_ident as personident FROM motesvar
            UNION ALL
            SELECT arbeidstaker_ident as personident FROM dialogmote_statusendring
            UNION ALL
            SELECT fnr as personident FROM person_oppgave
        ) identer
        WHERE personident = ?
    """

fun DatabaseInterface.getIdentCount(
    identer: List<PersonIdent>,
): Int =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetIdentCount).use<PreparedStatement, Int> {
            var count = 0
            identer.forEach { ident ->
                it.setString(1, ident.value)
                count += it.executeQuery().toList { getInt(1) }.firstOrNull() ?: 0
            }
            return count
        }
    }
