package no.nav.syfo.testutil

import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.dialogmotestatusendring.domain.PDialogmoteStatusendring
import java.time.OffsetDateTime
import java.util.*

fun generatePDialogmotestatusendring(
    type: DialogmoteStatusendringType = DialogmoteStatusendringType.INNKALT,
    uuid: UUID = UUID.randomUUID(),
    endringTidspunkt: OffsetDateTime = OffsetDateTime.now(),
): PDialogmoteStatusendring {
    return PDialogmoteStatusendring(
        id = 1,
        uuid = UUID.randomUUID().toString(),
        moteUuid = uuid.toString(),
        arbeidstakerIdent = UserConstants.ARBEIDSTAKER_FNR.value,
        veilederIdent = UserConstants.VEILEDER_IDENT,
        type = type.name,
        endringTidspunkt = endringTidspunkt,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )
}
