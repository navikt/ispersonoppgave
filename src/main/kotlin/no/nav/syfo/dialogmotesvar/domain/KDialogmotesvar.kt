package no.nav.syfo.dialogmotesvar.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class KDialogmotesvar(
    val ident: PersonIdent,
    val svarType: DialogmoteSvartype,
    val senderType: SenderType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

fun KDialogmotesvar.toDialogmotesvar(moteuuid: UUID): Dialogmotesvar = Dialogmotesvar(
    uuid = UUID.randomUUID(),
    moteuuid = moteuuid,
    arbeidstakerIdent = this.ident,
    svarType = this.svarType,
    senderType = this.senderType,
    brevSentAt = this.brevSentAt,
    svarReceivedAt = this.svarReceivedAt,
)

enum class SenderType {
    ARBEIDSTAKER,
    ARBEIDSGIVER,
    BEHANDLER,
}
