package no.nav.syfo.database

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgaver
import no.nav.syfo.util.toTimestamp
import java.sql.Connection
import java.sql.SQLException

class PersonOppgaveRepository(private val database: DatabaseInterface) : IPersonOppgaveRepository {

    override fun getUbehandledePersonoppgaver(
        personIdent: PersonIdent,
        type: PersonOppgaveType,
        connection: Connection?
    ): List<PersonOppgave> = connection?.getUbehandledePersonOppgaver(personIdent = personIdent, type = type)
        ?: database.connection.use {
            it.getUbehandledePersonOppgaver(personIdent = personIdent, type = type)
        }

    override fun createPersonoppgave(personOppgave: PersonOppgave, connection: Connection?) {
        connection?.createPersonoppgave(personOppgave) ?: database.connection.use {
            it.createPersonoppgave(personOppgave)
            it.commit()
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
    }
}
