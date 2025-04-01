package no.nav.syfo.personoppgave

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.toList
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotesvar.domain.Dialogmotesvar
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.behandlerdialog.domain.Melding
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.oppfolgingsplanlps.kafka.KOppfolgingsplanLPS
import no.nav.syfo.util.convert
import no.nav.syfo.util.convertNullable
import no.nav.syfo.util.toTimestamp
import java.sql.*
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

const val queryUpdatePersonOppgaveBehandlet =
    """
    UPDATE PERSON_OPPGAVE
    SET behandlet_tidspunkt = ?, behandlet_veileder_ident = ?
    WHERE uuid = ?
    """

fun Connection.updatePersonOppgaveBehandlet(
    updatedPersonoppgave: PersonOppgave,
    commit: Boolean = false,
): Int {
    var updatedRows = 0
    this.prepareStatement(queryUpdatePersonOppgaveBehandlet).use {
        it.setTimestamp(1, updatedPersonoppgave.behandletTidspunkt.toTimestamp())
        it.setString(2, updatedPersonoppgave.behandletVeilederIdent)
        it.setString(3, updatedPersonoppgave.uuid.toString())
        updatedRows += it.executeUpdate()
    }
    if (updatedRows < 1) {
        throw SQLException("Updating oppgave failed, no rows affected.")
    }
    if (commit) {
        this.commit()
    }
    return updatedRows
}

fun DatabaseInterface.updatePersonOppgaveBehandlet(
    updatedPersonoppgave: PersonOppgave,
) {
    connection.use { connection ->
        connection.updatePersonOppgaveBehandlet(
            updatedPersonoppgave = updatedPersonoppgave,
            commit = true,
        )
    }
}

fun DatabaseInterface.updatePersonoppgaverBehandlet(
    updatedPersonoppgaver: List<PersonOppgave>,
): Int {
    var updatedRows = 0
    this.connection.use { connection ->
        updatedPersonoppgaver.forEach { personoppgave ->
            updatedRows += connection.updatePersonOppgaveBehandlet(
                updatedPersonoppgave = personoppgave,
            )
        }
        connection.commit()
    }

    return updatedRows
}

const val queryUpdatePersonOppgaveOversikthendelse =
    """
    UPDATE PERSON_OPPGAVE
    SET oversikthendelse_tidspunkt = ?
    WHERE uuid = ?
    """

fun DatabaseInterface.updatePersonOppgaveOversikthendelse(
    uuid: UUID,
) {
    val now = Timestamp.from(Instant.now())

    connection.use { connection ->
        connection.prepareStatement(queryUpdatePersonOppgaveOversikthendelse).use {
            it.setTimestamp(1, now)
            it.setString(2, uuid.toString())
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

fun Connection.createPersonOppgave(
    kOppfolgingsplanLPS: KOppfolgingsplanLPS,
    type: PersonOppgaveType,
) = createPersonOppgave(
    referanseUuid = UUID.fromString(kOppfolgingsplanLPS.uuid),
    personIdent = PersonIdent(kOppfolgingsplanLPS.fodselsnummer),
    virksomhetsnummer = Virksomhetsnummer(kOppfolgingsplanLPS.virksomhetsnummer),
    personOppgaveType = type,
)

fun Connection.createPersonOppgave(
    dialogmotesvar: Dialogmotesvar,
) = createPersonOppgave(
    referanseUuid = dialogmotesvar.moteuuid,
    personIdent = dialogmotesvar.arbeidstakerIdent,
    personOppgaveType = PersonOppgaveType.DIALOGMOTESVAR,
    sistEndret = Timestamp.from(dialogmotesvar.svarReceivedAt.toInstant()),
    publish = true,
)

fun Connection.createPersonOppgave(
    melding: Melding,
    personOppgaveType: PersonOppgaveType,
) = createPersonOppgave(
    referanseUuid = melding.referanseUuid,
    personIdent = melding.personIdent,
    personOppgaveType = personOppgaveType,
)

private fun Connection.createPersonOppgave(
    referanseUuid: UUID,
    personIdent: PersonIdent,
    virksomhetsnummer: Virksomhetsnummer? = null,
    personOppgaveType: PersonOppgaveType,
    sistEndret: Timestamp? = null,
    publish: Boolean = false,
): UUID {
    val uuid = UUID.randomUUID()
    val now = Timestamp.from(Instant.now())
    val sistEndretTidspunkt = sistEndret ?: now

    val personIdList = prepareStatement(queryCreatePersonOppgave).use {
        it.setString(1, uuid.toString())
        it.setString(2, referanseUuid.toString())
        it.setString(3, personIdent.value)
        it.setString(4, virksomhetsnummer?.value ?: "")
        it.setString(5, personOppgaveType.name)
        it.setTimestamp(6, now)
        it.setTimestamp(7, sistEndretTidspunkt)
        it.setBoolean(8, publish)
        it.executeQuery().toList { getInt("id") }
    }

    if (personIdList.size != 1) {
        throw SQLException("Creating personopppgave failed, no rows affected.")
    }
    return uuid
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
        behandlet_veileder_ident,
        publish) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
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
        it.setBoolean(10, true)
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

fun Connection.updatePersonoppgaveSetBehandlet(
    personoppgave: PersonOppgave,
) {
    val behandletTidspunkt = personoppgave.behandletTidspunkt?.toTimestamp()

    val behandletOppgaver = prepareStatement(queryUpdatePersonoppgave).use {
        it.setTimestamp(1, behandletTidspunkt)
        it.setString(2, personoppgave.behandletVeilederIdent)
        it.setTimestamp(3, Timestamp.valueOf(personoppgave.sistEndret))
        it.setBoolean(4, personoppgave.publish)
        it.setObject(5, personoppgave.publishedAt)
        it.setString(6, personoppgave.uuid.toString())

        it.executeUpdate()
    }

    if (behandletOppgaver != 1) {
        throw SQLException("Updating oppgave failed, no rows affected.")
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
        publish = getBoolean("publish"),
        publishedAt = getObject("published_at", OffsetDateTime::class.java),
        duplikatReferanseUuid = getString("duplikat_referanse_uuid")?.let { UUID.fromString(it) },
    )
