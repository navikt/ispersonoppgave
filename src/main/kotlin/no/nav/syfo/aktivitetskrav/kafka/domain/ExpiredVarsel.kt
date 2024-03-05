package no.nav.syfo.aktivitetskrav.kafka.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
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
) {
    fun toPersonoppgave(): PersonOppgave = PersonOppgave(
        personIdent = personIdent,
        referanseUuid = varselUuid,
        type = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
        publish = true,
    )
}

enum class VarselType {
    FORHANDSVARSEL_STANS_AV_SYKEPENGER
}
