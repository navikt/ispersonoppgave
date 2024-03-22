package no.nav.syfo.arbeidsuforhet.kafka

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class ExpiredForhandsvarsel(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdent,
    val svarfrist: LocalDate,
) {
    fun toPersonoppgave(): PersonOppgave = PersonOppgave(
        personIdent = personIdent,
        referanseUuid = uuid,
        type = PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG,
        publish = true,
    )
}
