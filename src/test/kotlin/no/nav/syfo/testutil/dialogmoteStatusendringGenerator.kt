package no.nav.syfo.testutil

import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import java.time.OffsetDateTime
import java.util.*

fun generateDialogmotestatusendring(
    type: DialogmoteStatusendringType = DialogmoteStatusendringType.INNKALT,
    uuid: UUID = UUID.randomUUID(),
    endringTidspunkt: OffsetDateTime = OffsetDateTime.now(),
): DialogmoteStatusendring {
    return DialogmoteStatusendring(
        uuid = UUID.randomUUID(),
        personIdent = UserConstants.ARBEIDSTAKER_FNR,
        type = type,
        endringTidspunkt = endringTidspunkt,
        dialogmoteUuid = uuid,
        veilederIdent = UserConstants.VEILEDER_IDENT,
    )
}
