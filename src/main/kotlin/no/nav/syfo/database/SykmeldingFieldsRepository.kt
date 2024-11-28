package no.nav.syfo.database

import no.nav.syfo.domain.PersonIdent
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
            it.setString(4, tiltakNav ?: "")
            it.setString(5, tiltakAndre ?: "")
            it.setString(6, bistand ?: "")
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
        it.setString(1, personident.value)
        it.setString(2, tiltakNav ?: "")
        it.setString(3, tiltakAndre ?: "")
        it.setString(4, bistand ?: "")
        it.executeQuery().toList { getInt("id") }.isNotEmpty()
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
                bistand)
                VALUES (DEFAULT, ?, ?, ?, ?, ?, ?) RETURNING id
            """

        private const val FIND_EXISTING_FROM_SYKMELDING_FIELDS =
            """
                SELECT id FROM SYKMELDING WHERE 
                personident = ? AND
                tiltak_nav = ? AND
                tiltak_andre = ? AND
                bistand = ?
            """
    }
}
