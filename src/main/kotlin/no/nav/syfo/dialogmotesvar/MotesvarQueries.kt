package no.nav.syfo.dialogmotesvar

import no.nav.syfo.database.toList
import no.nav.syfo.dialogmotesvar.domain.Dialogmotesvar
import no.nav.syfo.dialogmotesvar.domain.PMotesvar
import java.sql.*
import java.time.OffsetDateTime
import java.util.*

const val queryCreateMotesvar =
    """INSERT INTO motesvar (
        id,
        uuid,
        mote_uuid,
        arbeidstaker_ident,
        svar_type,
        sender_type,
        brev_sent_at,
        svar_received_at,
        created_at, 
        updated_at) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createMotesvar(
    dialogmotesvar: Dialogmotesvar,
) {
    val now = OffsetDateTime.now()

    val motesvarIDs = prepareStatement(queryCreateMotesvar).use {
        it.setString(1, dialogmotesvar.uuid.toString())
        it.setString(2, dialogmotesvar.moteuuid.toString())
        it.setString(3, dialogmotesvar.arbeidstakerIdent.value)
        it.setString(4, dialogmotesvar.svarType.name)
        it.setString(5, dialogmotesvar.senderType.name)
        it.setObject(6, dialogmotesvar.brevSentAt)
        it.setObject(7, dialogmotesvar.svarReceivedAt)
        it.setObject(8, now)
        it.setObject(9, now)
        it.executeQuery().toList { getInt("id") }
    }

    if (motesvarIDs.size != 1) {
        throw SQLException("Creating motesvar failed, no rows affected.")
    }
}

const val queryGetMotesvar =
    """
        SELECT *
        FROM motesvar
        WHERE mote_uuid = ?
    """

fun Connection.getMotesvar(
    moteUuid: UUID,
) = prepareStatement(queryGetMotesvar).use {
    it.setString(1, moteUuid.toString())
    it.executeQuery().toList { toPMotesvar() }
}

fun ResultSet.toPMotesvar(): PMotesvar =
    PMotesvar(
        id = getInt("id"),
        uuid = getString("uuid"),
        moteUuid = getString("mote_uuid"),
        arbeidstakerIdent = getString("arbeidstaker_ident"),
        svarType = getString("svar_type"),
        senderType = getString("sender_type"),
        brevSentAt = getObject("brev_sent_at", OffsetDateTime::class.java),
        svarReceivedAt = getObject("svar_received_at", OffsetDateTime::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    )
