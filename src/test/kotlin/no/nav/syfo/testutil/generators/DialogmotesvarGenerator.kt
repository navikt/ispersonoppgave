package no.nav.syfo.testutil.generators

import no.nav.syfo.infrastructure.kafka.dialogmotesvar.DialogmoteSvartype
import no.nav.syfo.infrastructure.kafka.dialogmotesvar.Dialogmotesvar
import no.nav.syfo.infrastructure.kafka.dialogmotesvar.KDialogmotesvar
import no.nav.syfo.infrastructure.kafka.dialogmotesvar.SenderType
import no.nav.syfo.testutil.UserConstants
import java.time.OffsetDateTime
import java.util.*

fun generateDialogmotesvar(
    moteuuid: UUID = UUID.randomUUID(),
    svartype: DialogmoteSvartype = DialogmoteSvartype.KOMMER,
    svarReceivedAt: OffsetDateTime = OffsetDateTime.now(),
    senderType: SenderType = SenderType.ARBEIDSTAKER,
    svarTekst: String? = null,
) = Dialogmotesvar(
    uuid = UUID.randomUUID(),
    moteuuid = moteuuid,
    arbeidstakerIdent = UserConstants.ARBEIDSTAKER_FNR,
    svarType = svartype,
    senderType = senderType,
    brevSentAt = OffsetDateTime.now(),
    svarReceivedAt = svarReceivedAt,
    svarTekst = svarTekst,
)

fun generateKDialogmotesvar() = KDialogmotesvar(
    ident = UserConstants.ARBEIDSTAKER_FNR,
    svarType = DialogmoteSvartype.KOMMER,
    senderType = SenderType.ARBEIDSTAKER,
    brevSentAt = OffsetDateTime.now(),
    svarReceivedAt = OffsetDateTime.now(),
    svarTekst = null,
)
