package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.OffsetDateTime
import java.util.*

data class AktivitetskravVurdering(
    val uuid: UUID,
    val personIdent: PersonIdent,
    val createdAt: OffsetDateTime,
    val status: AktivitetskravStatus,
    val vurdertAv: String,
    val sistVurdert: OffsetDateTime,
) {
    fun isFinalVurdering() = status in finalVurderinger

    infix fun happenedAfter(personOppgave: PersonOppgave) =
        sistVurdert.toLocalDateTimeOslo().isAfter(personOppgave.opprettet)
}

private val finalVurderinger = EnumSet.of(
    AktivitetskravStatus.UNNTAK,
    AktivitetskravStatus.OPPFYLT,
    AktivitetskravStatus.IKKE_OPPFYLT,
    AktivitetskravStatus.IKKE_AKTUELL,
)

enum class AktivitetskravStatus {
    NY,
    AVVENT,
    UNNTAK,
    OPPFYLT,
    AUTOMATISK_OPPFYLT,
    FORHANDSVARSEL,
    STANS,
    IKKE_OPPFYLT,
    IKKE_AKTUELL,
    LUKKET,
}
