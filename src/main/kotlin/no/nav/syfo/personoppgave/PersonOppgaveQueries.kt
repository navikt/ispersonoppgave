package no.nav.syfo.personoppgave

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.toList
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotesvar.domain.Dialogmotesvar
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.log
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.util.*
import java.sql.*
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

const val queryGetPersonOppgaveListForFnr =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE fnr = ?
    """

fun DatabaseInterface.getPersonOppgaveList(personIdent: PersonIdent): List<PPersonOppgave> {
    return connection.use { connection ->
        connection.getPersonOppgaver(personIdent)
    }
}

fun Connection.getPersonOppgaver(personIdent: PersonIdent): List<PPersonOppgave> {
    return prepareStatement(queryGetPersonOppgaveListForFnr).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList { toPPersonOppgave() }
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

const val queryGetPersonOppgaverByReferanseUUID =
    """
    SELECT *
    FROM PERSON_OPPGAVE
    WHERE referanse_uuid = ?
    """

fun Connection.getPersonOppgaveByReferanseUuid(referanseUuid: UUID): PPersonOppgave? {
    return prepareStatement(queryGetPersonOppgaverByReferanseUUID).use {
        it.setString(1, referanseUuid.toString())
        it.executeQuery().toList {
            toPPersonOppgave()
        }.firstOrNull()
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
        sist_endret, 
        publish) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
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
            it.setBoolean(8, true)
            it.executeQuery().toList { getInt("id") }
        }

        if (personIdList.size != 1) {
            throw SQLException("Creating person failed, no rows affected.")
        }
        connection.commit()

        return Pair(personIdList.first(), UUID.fromString(uuid))
    }
}

fun Connection.createPersonOppgave( // TODO: send in oppgave instead of dialogmotesvar
    dialogmotesvar: Dialogmotesvar,
    uuid: UUID,
) {
    val now = Timestamp.from(Instant.now())

    val personIdList = prepareStatement(queryCreatePersonOppgave).use {
        it.setString(1, uuid.toString())
        it.setString(2, dialogmotesvar.moteuuid.toString())
        it.setString(3, dialogmotesvar.arbeidstakerIdent.value)
        it.setString(4, "")
        it.setString(5, PersonOppgaveType.DIALOGMOTESVAR.name)
        it.setTimestamp(6, now)
        it.setTimestamp(7, Timestamp.from(dialogmotesvar.svarReceivedAt.toInstant()))
        it.executeQuery().toList { getInt("id") }
    }

    if (personIdList.size != 1) {
        throw SQLException("Creating person failed, no rows affected.")
    }
}

const val queryCreateBehandletPersonOppgave =
    """INSERT INTO PERSON_OPPGAVE (
        id,
        uuid,
        referanse_uuid,
        fnr,
        virksomhetsnummer,
        type,
        opprettet,    
        sist_endret, 
        behandlet_tidspunkt,
        behandlet_veileder_ident) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createBehandletPersonoppgave(
    dialogmotestatus: DialogmoteStatusendring,
    uuid: UUID,
) {
    val now = Timestamp.from(Instant.now())
    val endringTidspunkt = Timestamp.from(dialogmotestatus.endringTidspunkt.toInstant())

    val personIdList = prepareStatement(queryCreateBehandletPersonOppgave).use {
        it.setString(1, uuid.toString())
        it.setString(2, dialogmotestatus.dialogmoteUuid.toString())
        it.setString(3, dialogmotestatus.personIdent.value)
        it.setString(4, "")
        it.setString(5, PersonOppgaveType.DIALOGMOTESVAR.name)
        it.setTimestamp(6, now)
        it.setTimestamp(7, endringTidspunkt)
        it.setTimestamp(8, endringTidspunkt)
        it.setString(9, dialogmotestatus.veilederIdent)
        it.executeQuery().toList { getInt("id") }
    }

    if (personIdList.size != 1) {
        throw SQLException("Creating person failed, no rows affected.")
    }
}

const val queryUpdatePersonoppgave =
    """
    UPDATE PERSON_OPPGAVE
    SET behandlet_tidspunkt = ?, behandlet_veileder_ident = ?, sist_endret = ?, publish = ?, published_at = ?
    WHERE uuid = ?
    """

fun Connection.updatePersonoppgave(
    personoppgave: PersonOppgave,
) {
    val behandletTidspunkt = personoppgave.behandletTidspunkt?.let { Timestamp.valueOf(it) }
    val publishedAt = personoppgave.publishedAt?.let { Timestamp.from(it.toInstant()) }

    val behandletOppgaver = prepareStatement(queryUpdatePersonoppgave).use {
        it.setTimestamp(1, behandletTidspunkt)
        it.setString(2, personoppgave.behandletVeilederIdent)
        it.setTimestamp(3, Timestamp.valueOf(personoppgave.sistEndret))
        it.setBoolean(4, personoppgave.publish)
        it.setObject(5, publishedAt)
        it.setString(6, personoppgave.uuid.toString())

        it.executeUpdate()
    }

    if (behandletOppgaver != 1) {
        throw SQLException("Updating oppgave failed, no rows affected.")
    }
}

const val queryBehandleOppgaveByReferanseUuid =
    """
    UPDATE PERSON_OPPGAVE
    SET behandlet_tidspunkt = ?, behandlet_veileder_ident = ?, sist_endret = ?
    WHERE referanse_uuid = ?
    """

fun Connection.behandleOppgave(
    statusendring: DialogmoteStatusendring,
): Boolean {
    val now = Timestamp.from(Instant.now())
    val sistEndretTimestamp = Timestamp.from(statusendring.endringTidspunkt.toInstant())
    val behandletOppgaver = this.prepareStatement(queryBehandleOppgaveByReferanseUuid).use {
        it.setTimestamp(1, now)
        it.setString(2, statusendring.veilederIdent)
        it.setTimestamp(3, sistEndretTimestamp)
        it.setString(4, statusendring.dialogmoteUuid.toString())
        it.executeUpdate()
    }

    log.info("TRACE: BehandletOppgaver: $behandletOppgaver")
    return behandletOppgaver >= 1
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
        publish = getBoolean("publish"),
        publishedAt = getObject("published_at", OffsetDateTime::class.java),
    )
