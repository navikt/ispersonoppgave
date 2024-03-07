package no.nav.syfo.database

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgaver
import no.nav.syfo.util.toTimestamp
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp

class PersonOppgaveRepository(private val database: DatabaseInterface) {

    fun getUbehandledePersonoppgaver(
        personIdent: PersonIdent,
        type: PersonOppgaveType,
        connection: Connection? = null,
    ): List<PersonOppgave> = connection?.getUbehandledePersonOppgaver(personIdent = personIdent, type = type)
        ?: database.connection.use {
            it.getUbehandledePersonOppgaver(personIdent = personIdent, type = type)
        }

    fun createPersonoppgave(personOppgave: PersonOppgave, connection: Connection? = null) {
        connection?.createPersonoppgave(personOppgave) ?: database.connection.use {
            it.createPersonoppgave(personOppgave)
            it.commit()
        }
    }

    fun updatePersonoppgaveBehandlet(personOppgave: PersonOppgave, connection: Connection? = null) {
        connection?.updatePersonoppgaveBehandlet(personOppgave) ?: database.connection.use {
            it.updatePersonoppgaveBehandlet(personOppgave)
            it.commit()
        }
    }

    private fun Connection.updatePersonoppgaveBehandlet(personOppgave: PersonOppgave) {
        val behandletOppgaver = prepareStatement(UPDATE_PERSONOPPGAVE_BEHANDLET).use {
            it.setTimestamp(1, personOppgave.behandletTidspunkt?.toTimestamp())
            it.setString(2, personOppgave.behandletVeilederIdent)
            it.setTimestamp(3, Timestamp.valueOf(personOppgave.sistEndret))
            it.setBoolean(4, personOppgave.publish)
            it.setObject(5, personOppgave.publishedAt)
            it.setString(6, personOppgave.uuid.toString())

            it.executeUpdate()
        }

        if (behandletOppgaver != 1) {
            throw SQLException("Updating oppgave failed, no rows affected.")
        }
    }

    private fun Connection.createPersonoppgave(personOppgave: PersonOppgave) {
        val personIdList = prepareStatement(CREATE_PERSONOPPGAVE).use {
            it.setString(1, personOppgave.uuid.toString())
            it.setString(2, personOppgave.referanseUuid.toString())
            it.setString(3, personOppgave.personIdent.value)
            it.setString(4, personOppgave.virksomhetsnummer?.value ?: "")
            it.setString(5, personOppgave.type.name)
            it.setTimestamp(6, personOppgave.opprettet.toTimestamp())
            it.setTimestamp(7, personOppgave.sistEndret.toTimestamp())
            it.setBoolean(8, personOppgave.publish)
            it.executeQuery().toList { getInt("id") }
        }

        if (personIdList.size != 1) {
            throw SQLException("Creating personopppgave failed, no rows affected.")
        }
    }

    private fun Connection.getUbehandledePersonOppgaver(personIdent: PersonIdent, type: PersonOppgaveType): List<PersonOppgave> = prepareStatement(
        GET_UBEHANDLEDE_PERSONOPPGAVER
    ).use {
        it.setString(1, personIdent.value)
        it.setString(2, type.name)
        it.executeQuery().toList { toPPersonOppgave() }
    }.toPersonOppgaver()

    companion object {
        private const val GET_UBEHANDLEDE_PERSONOPPGAVER =
            """
                SELECT *
                FROM PERSON_OPPGAVE
                WHERE fnr = ?
                  AND type = ?
                  AND behandlet_tidspunkt IS NULL
                  AND behandlet_veileder_ident IS NULL
            """

        private const val CREATE_PERSONOPPGAVE =
            """
                INSERT INTO PERSON_OPPGAVE (
                id,
                uuid,
                referanse_uuid,
                fnr,
                virksomhetsnummer,
                type,
                opprettet,
                sist_endret, 
                publish) 
                VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """

        private const val UPDATE_PERSONOPPGAVE_BEHANDLET =
            """
                UPDATE PERSON_OPPGAVE
                SET behandlet_tidspunkt = ?, behandlet_veileder_ident = ?, sist_endret = ?, publish = ?, published_at = ?
                WHERE uuid = ?
            """
    }
}
