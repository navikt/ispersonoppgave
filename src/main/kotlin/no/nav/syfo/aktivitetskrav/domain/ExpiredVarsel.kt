package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ExpiredVarsel(
    val aktivitetskravUuid: UUID,
    val varselUuid: UUID,
    val createdAt: LocalDateTime,
    val personIdent: PersonIdent,
    val varselType: VarselType,
    val svarfrist: LocalDate,
)

enum class VarselType {
    FORHANDSVARSEL_STANS_AV_SYKEPENGER
}
