package no.nav.syfo.dialogmotesvar

import no.nav.syfo.personoppgave.infrastructure.database.toList
import no.nav.syfo.dialogmotesvar.domain.Dialogmotesvar
import no.nav.syfo.dialogmotesvar.domain.PDialogmotesvar
import java.sql.*
import java.time.OffsetDateTime
import java.util.*

const val queryCreateDialogmotesvar =
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

fun Connection.createDialogmotesvar(
    dialogmotesvar: Dialogmotesvar,
) {
    val now = OffsetDateTime.now()

    val motesvarIDs = prepareStatement(queryCreateDialogmotesvar).use {
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
        throw SQLException("Creating dialogmotesvar failed, no rows affected.")
    }
}

const val queryGetDialogmotesvar =
    """
        SELECT *
        FROM motesvar
        WHERE mote_uuid = ?
    """

fun Connection.getDialogmotesvar(
    moteUuid: UUID,
) = prepareStatement(queryGetDialogmotesvar).use {
    it.setString(1, moteUuid.toString())
    it.executeQuery().toList { toPDialogmotesvar() }
}

fun ResultSet.toPDialogmotesvar(): PDialogmotesvar =
    PDialogmotesvar(
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
