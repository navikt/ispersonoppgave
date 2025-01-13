package no.nav.syfo.database

import no.nav.syfo.domain.PersonIdent
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.time.OffsetDateTime

const val DUPLICATE_COUNT_DEFAULT = 0

class SykmeldingFieldsRepository() {

    fun createPersonoppgaveSykmeldingFields(
        personoppgaveId: Int,
        tiltakNav: String?,
        tiltakAndre: String?,
        bistand: String?,
        connection: Connection,
    ) {
        val idList = connection.prepareStatement(CREATE_SYKMELDING_FIELDS).use {
            it.setInt(1, personoppgaveId)
            it.setObject(2, OffsetDateTime.now())
            it.setString(3, tiltakNav?.md5() ?: "")
            it.setString(4, tiltakAndre?.md5() ?: "")
            it.setString(5, bistand?.md5() ?: "")
            it.setInt(6, DUPLICATE_COUNT_DEFAULT)
            it.executeQuery().toList { getInt("id") }
        }

        if (idList.size != 1) {
            throw SQLException("Creating sykmelding failed, no rows affected.")
        }
    }

    fun findExistingPersonoppgaveFromSykmeldingFields(
        personident: PersonIdent,
        tiltakNav: String?,
        tiltakAndre: String?,
        bistand: String?,
        connection: Connection,
    ) = connection.prepareStatement(FIND_EXISTING_FROM_SYKMELDING_FIELDS).use {
        it.setObject(1, OffsetDateTime.now().minusMonths(6))
        it.setString(2, personident.value)
        it.setString(3, tiltakNav?.md5() ?: "")
        it.setString(4, tiltakAndre?.md5() ?: "")
        it.setString(5, bistand?.md5() ?: "")
        it.executeQuery().toList { Pair(getInt("id"), getString("referanse_uuid")) }
    }

    fun incrementDuplicateCount(
        personoppgaveId: Int,
        connection: Connection,
    ) {
        connection.prepareStatement(INCREMENT_DUPLICATE_COUNT).use {
            it.setInt(1, personoppgaveId)
            it.executeUpdate()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(this.toByteArray())
        return digest.toHexString()
    }

    companion object {
        private const val CREATE_SYKMELDING_FIELDS =
            """
                INSERT INTO SYKMELDING (
                id,
                personoppgave_id,
                created_at,
                tiltak_nav,
                tiltak_andre,
                bistand,
                duplicate_count)
                VALUES (DEFAULT, ?, ?, ?, ?, ?, ?) RETURNING id
            """

        private const val FIND_EXISTING_FROM_SYKMELDING_FIELDS =
            """
                SELECT p.id,p.referanse_uuid FROM SYKMELDING s INNER JOIN PERSON_OPPGAVE p ON (s.personoppgave_id=p.id) WHERE 
                s.created_at > ? AND
                p.fnr = ? AND
                s.tiltak_nav = ? AND
                s.tiltak_andre = ? AND
                s.bistand = ?
            """

        private const val INCREMENT_DUPLICATE_COUNT =
            """
                UPDATE SYKMELDING SET duplicate_count = duplicate_count+1 WHERE personoppgave_id = ?
            """
    }
}
