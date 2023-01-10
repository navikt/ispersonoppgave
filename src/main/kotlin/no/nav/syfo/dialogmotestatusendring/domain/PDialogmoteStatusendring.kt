package no.nav.syfo.dialogmotestatusendring.domain

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
