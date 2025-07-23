package no.nav.syfo.dialogmotestatusendring

import no.nav.syfo.personoppgave.infrastructure.database.toList
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.domain.PDialogmoteStatusendring
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

const val queryCreateDialogmoteStatusendring =
    """INSERT INTO dialogmote_statusendring (
        id,
        uuid,
        mote_uuid,
        arbeidstaker_ident,
        veileder_ident,
        type,
        endring_tidspunkt,
        created_at,
        updated_at) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createDialogmoteStatusendring(
    dialogmoteStatusendring: DialogmoteStatusendring,
) {
    val now = OffsetDateTime.now()

    prepareStatement(queryCreateDialogmoteStatusendring).use {
        it.setString(1, dialogmoteStatusendring.uuid.toString())
        it.setString(2, dialogmoteStatusendring.dialogmoteUuid.toString())
        it.setString(3, dialogmoteStatusendring.personIdent.value)
        it.setString(4, dialogmoteStatusendring.veilederIdent)
        it.setString(5, dialogmoteStatusendring.type.name)
        it.setObject(6, dialogmoteStatusendring.endringTidspunkt)
        it.setObject(7, now)
        it.setObject(8, now)
        it.executeQuery().toList { getInt("id") }
    }
}

const val queryGetDialogmoteStatusendring =
    """
        SELECT *
        FROM dialogmote_statusendring
        WHERE mote_uuid = ?
    """

fun Connection.getDialogmoteStatusendring(
    moteUuid: UUID,
) = prepareStatement(queryGetDialogmoteStatusendring).use {
    it.setString(1, moteUuid.toString())
    it.executeQuery().toList {
        toPDialogmoteStatusendring()
    }
}

fun ResultSet.toPDialogmoteStatusendring(): PDialogmoteStatusendring =
    PDialogmoteStatusendring(
        id = getInt("id"),
        uuid = getString("uuid"),
        moteUuid = getString("mote_uuid"),
        arbeidstakerIdent = getString("arbeidstaker_ident"),
        veilederIdent = getString("veileder_ident"),
        type = getString("type"),
        endringTidspunkt = getObject("endring_tidspunkt", OffsetDateTime::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    )
