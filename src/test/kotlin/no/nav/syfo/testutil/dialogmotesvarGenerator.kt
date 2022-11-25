package no.nav.syfo.testutil

import no.nav.syfo.dialogmotesvar.domain.*
import java.time.OffsetDateTime
import java.util.*

fun generateDialogmotesvar(
    moteuuid: UUID = UUID.randomUUID(),
    svartype: DialogmoteSvartype = DialogmoteSvartype.KOMMER,
    svarReceivedAt: OffsetDateTime = OffsetDateTime.now(),
) = Dialogmotesvar(
    moteuuid = moteuuid,
    arbeidstakerIdent = UserConstants.ARBEIDSTAKER_FNR,
    svarType = svartype,
    senderType = SenderType.ARBEIDSTAKER,
    brevSentAt = OffsetDateTime.now(),
    svarReceivedAt = svarReceivedAt,
)