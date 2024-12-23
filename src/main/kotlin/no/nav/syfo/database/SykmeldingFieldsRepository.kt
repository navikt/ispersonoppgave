package no.nav.syfo.database

import no.nav.syfo.domain.PersonIdent
import java.security.MessageDigest
import java.sql.Connection
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID

class SykmeldingFieldsRepository() {

    fun createPersonoppgaveSykmeldingFields(
        referanseUUID: UUID,
        personident: PersonIdent,
        tiltakNav: String?,
        tiltakAndre: String?,
        bistand: String?,
        connection: Connection,
    ) {
        val idList = connection.prepareStatement(CREATE_SYKMELDING_FIELDS).use {
            it.setString(1, referanseUUID.toString())
            it.setObject(2, OffsetDateTime.now())
            it.setString(3, personident.value)
            it.setString(4, tiltakNav?.md5() ?: "")
            it.setString(5, tiltakAndre?.md5() ?: "")
            it.setString(6, bistand?.md5() ?: "")
            it.setInt(7, 0)
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
        it.executeQuery().toList { getString("referanse_uuid") }
    }

    fun incrementDuplicateCount(
        referanseUUID: UUID,
        connection: Connection,
    ) {
        connection.prepareStatement(INCREMENT_DUPLICATE_COUNT).use {
            it.setString(1, referanseUUID.toString())
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
                referanse_uuid,
                created_at,
                personident,
                tiltak_nav,
                tiltak_andre,
                bistand,
                duplicate_count)
                VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """

        private const val FIND_EXISTING_FROM_SYKMELDING_FIELDS =
            """
                SELECT referanse_uuid FROM SYKMELDING WHERE 
                created_at > ? AND
                personident = ? AND
                tiltak_nav = ? AND
                tiltak_andre = ? AND
                bistand = ?
            """

        private const val INCREMENT_DUPLICATE_COUNT =
            """
                UPDATE SYKMELDING SET duplicate_count = duplicate_count+1 WHERE referanse_uuid = ?
            """
    }
}
