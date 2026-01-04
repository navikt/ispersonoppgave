package no.nav.syfo.infrastructure.database.queries

import no.nav.syfo.domain.DialogmoteStatusendringType
import java.time.OffsetDateTime

data class PDialogmoteStatusendring(
    val id: Int,
    val uuid: String,
    val moteUuid: String,
    val arbeidstakerIdent: String,
    val veilederIdent: String,
    val type: String,
    val endringTidspunkt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

fun PDialogmoteStatusendring.didFinishDialogmote(): Boolean {
    return when (DialogmoteStatusendringType.valueOf(type)) {
        DialogmoteStatusendringType.FERDIGSTILT, DialogmoteStatusendringType.AVLYST, DialogmoteStatusendringType.LUKKET -> true
        else -> false
    }
}
