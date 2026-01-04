package no.nav.syfo.testutil.generators

import no.nav.syfo.domain.DialogmoteStatusendring
import no.nav.syfo.domain.DialogmoteStatusendringType
import no.nav.syfo.infrastructure.database.queries.PDialogmoteStatusendring
import no.nav.syfo.testutil.UserConstants
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
