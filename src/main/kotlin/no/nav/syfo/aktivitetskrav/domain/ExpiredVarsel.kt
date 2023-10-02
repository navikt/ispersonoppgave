package no.nav.syfo.aktivitetskrav.domain

import java.time.LocalDate
import java.util.UUID

data class ExpiredVarsel(
    val uuid: UUID,
    val personIdent: String,
    val svarfrist: LocalDate,
)
