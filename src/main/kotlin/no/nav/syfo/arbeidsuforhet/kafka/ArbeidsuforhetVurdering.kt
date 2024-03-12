package no.nav.syfo.arbeidsuforhet.kafka

import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.OffsetDateTime
import java.util.*

data class ArbeidsuforhetVurdering(
    val uuid: UUID,
    val personident: String,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val type: VurderingType,
    val begrunnelse: String,
)

enum class VurderingType {
    FORHANDSVARSEL, OPPFYLT, AVSLAG
}

infix fun ArbeidsuforhetVurdering.behandler(personOppgave: PersonOppgave): Boolean =
    this.isFinal() && this.createdAt.toLocalDateTimeOslo().isAfter(personOppgave.opprettet)

private fun ArbeidsuforhetVurdering.isFinal(): Boolean = when (type) {
    VurderingType.FORHANDSVARSEL -> false
    VurderingType.OPPFYLT -> true
    VurderingType.AVSLAG -> true
}
