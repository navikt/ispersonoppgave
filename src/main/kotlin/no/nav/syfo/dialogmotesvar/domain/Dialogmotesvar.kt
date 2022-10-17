package no.nav.syfo.dialogmotesvar.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class Dialogmotesvar(
    val moteuuid: UUID,
    val arbeidstakerIdent: PersonIdent,
    val svarType: DialogmoteSvartype,
    val senderType: SenderType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)
