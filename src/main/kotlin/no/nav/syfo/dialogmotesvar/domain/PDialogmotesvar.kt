package no.nav.syfo.dialogmotesvar.domain

import java.time.OffsetDateTime

data class PDialogmotesvar(
    val id: Int,
    val uuid: String,
    val moteUuid: String,
    val arbeidstakerIdent: String,
    val svarType: String,
    val senderType: String,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
