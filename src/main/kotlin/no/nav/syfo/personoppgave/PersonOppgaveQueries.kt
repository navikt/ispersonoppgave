package no.nav.syfo.personoppgave

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.toList
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.util.convert
import no.nav.syfo.util.convertNullable
import java.sql.*
import java.time.Instant
import java.util.*

const val queryGetPersonOppgaveListForFnr =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE fnr = ?
    """

fun DatabaseInterface.getPersonOppgaveList(fnr: Fodselsnummer): List<PPersonOppgave> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetPersonOppgaveListForFnr).use {
            it.setString(1, fnr.value)
            it.executeQuery().toList { toPPersonOppgave() }
        }
    }
}

const val queryGetPersonOppgaveListForUUID =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE uuid = ?
    """

fun DatabaseInterface.getPersonOppgaveList(uuid: UUID): List<PPersonOppgave> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetPersonOppgaveListForUUID).use {
            it.setString(1, uuid.toString())
            it.executeQuery().toList { toPPersonOppgave() }
        }
    }
}

const val queryUpdatePersonOppgaveBehandlet =
    """
    UPDATE PERSON_OPPGAVE
    SET behandlet_tidspunkt = ?, behandlet_veileder_ident = ?
    WHERE uuid = ?
    """

fun DatabaseInterface.updatePersonOppgaveBehandlet(
    uuid: UUID,
    veilederIdent: String
) {
    val now = Timestamp.from(Instant.now())
    connection.use { connection ->
        connection.prepareStatement(queryUpdatePersonOppgaveBehandlet).use {
            it.setTimestamp(1, now)
            it.setString(2, veilederIdent)
            it.setString(3, uuid.toString())
            it.execute()
        }
        connection.commit()
    }
}

const val queryUpdatePersonOppgaveOversikthendelse =
    """
    UPDATE PERSON_OPPGAVE
    SET oversikthendelse_tidspunkt = ?
    WHERE id = ?
    """

fun DatabaseInterface.updatePersonOppgaveOversikthendelse(
    id: Int
) {
    val now = Timestamp.from(Instant.now())

    connection.use { connection ->
        connection.prepareStatement(queryUpdatePersonOppgaveOversikthendelse).use {
            it.setTimestamp(1, now)
            it.setInt(2, id)
            it.execute()
        }
        connection.commit()
    }
}

const val queryCreatePersonOppgave =
    """INSERT INTO PERSON_OPPGAVE (
        id,
        uuid,
        referanse_uuid,
        fnr,
        virksomhetsnummer,
        type,
        opprettet,
        sist_endret) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun DatabaseInterface.createPersonOppgave(
    kOppfolgingsplanLPSNAV: KOppfolgingsplanLPSNAV,
    type: PersonOppgaveType
): Pair<Int, UUID> {
    val uuid = UUID.randomUUID().toString()
    val now = Timestamp.from(Instant.now())

    connection.use { connection ->
        val personIdList = connection.prepareStatement(queryCreatePersonOppgave).use {
            it.setString(1, uuid)
            it.setString(2, kOppfolgingsplanLPSNAV.getUuid())
            it.setString(3, kOppfolgingsplanLPSNAV.getFodselsnummer())
            it.setString(4, kOppfolgingsplanLPSNAV.getVirksomhetsnummer())
            it.setString(5, type.name)
            it.setTimestamp(6, now)
            it.setTimestamp(7, now)
            it.executeQuery().toList { getInt("id") }
        }

        if (personIdList.size != 1) {
            throw SQLException("Creating person failed, no rows affected.")
        }
        connection.commit()

        return Pair(personIdList.first(), UUID.fromString(uuid))
    }
}

fun ResultSet.toPPersonOppgave(): PPersonOppgave =
    PPersonOppgave(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        referanseUuid = UUID.fromString(getString("referanse_uuid")),
        fnr = getString("fnr"),
        virksomhetsnummer = getString("virksomhetsnummer"),
        type = getString("type"),
        oversikthendelseTidspunkt = convertNullable(getTimestamp("oversikthendelse_tidspunkt")),
        behandletTidspunkt = convertNullable(getTimestamp("behandlet_tidspunkt")),
        behandletVeilederIdent = getString("behandlet_veileder_ident"),
        opprettet = convert(getTimestamp("opprettet")),
        sistEndret = convert(getTimestamp("sist_endret")),
    )
