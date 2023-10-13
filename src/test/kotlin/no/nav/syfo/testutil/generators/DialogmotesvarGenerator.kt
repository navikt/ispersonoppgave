package no.nav.syfo.testutil.generators

import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.testutil.UserConstants
import java.time.OffsetDateTime
import java.util.*

fun generateDialogmotesvar(
    moteuuid: UUID = UUID.randomUUID(),
    svartype: DialogmoteSvartype = DialogmoteSvartype.KOMMER,
    svarReceivedAt: OffsetDateTime = OffsetDateTime.now(),
) = Dialogmotesvar(
    uuid = UUID.randomUUID(),
    moteuuid = moteuuid,
    arbeidstakerIdent = UserConstants.ARBEIDSTAKER_FNR,
    svarType = svartype,
    senderType = SenderType.ARBEIDSTAKER,
    brevSentAt = OffsetDateTime.now(),
    svarReceivedAt = svarReceivedAt,
)

fun generateKDialogmotesvar() = KDialogmotesvar(
    ident = UserConstants.ARBEIDSTAKER_FNR,
    svarType = DialogmoteSvartype.KOMMER,
    senderType = SenderType.ARBEIDSTAKER,
    brevSentAt = OffsetDateTime.now(),
    svarReceivedAt = OffsetDateTime.now(),
)
