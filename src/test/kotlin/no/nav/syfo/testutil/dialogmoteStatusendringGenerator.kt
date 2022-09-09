package no.nav.syfo.testutil

import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import java.time.OffsetDateTime
import java.util.*

fun getDialogmotestatusendring(type: DialogmoteStatusendringType, uuid: UUID): DialogmoteStatusendring {
    return DialogmoteStatusendring(
        personIdent = UserConstants.ARBEIDSTAKER_FNR,
        type = type,
        endringTidspunkt = OffsetDateTime.now(),
        dialogmoteUuid = uuid,
        veilederIdent = UserConstants.VEILEDER_IDENT,
    )
}
