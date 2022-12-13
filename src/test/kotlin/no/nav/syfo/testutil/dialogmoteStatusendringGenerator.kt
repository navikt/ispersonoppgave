package no.nav.syfo.testutil

import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import java.time.OffsetDateTime
import java.util.*

fun generateDialogmotestatusendring(type: DialogmoteStatusendringType, uuid: UUID, now: OffsetDateTime): DialogmoteStatusendring {
    return DialogmoteStatusendring(
        uuid = UUID.randomUUID(),
        personIdent = UserConstants.ARBEIDSTAKER_FNR,
        type = type,
        endringTidspunkt = now,
        dialogmoteUuid = uuid,
        veilederIdent = UserConstants.VEILEDER_IDENT,
    )
}
