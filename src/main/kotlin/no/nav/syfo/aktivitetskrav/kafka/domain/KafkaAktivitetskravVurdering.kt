package no.nav.syfo.aktivitetskrav.kafka.domain

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class KafkaAktivitetskravVurdering(
    val uuid: String,
    val personIdent: String,
    val createdAt: OffsetDateTime,
    val status: String,
    val beskrivelse: String?,
    val arsaker: List<String>,
    val stoppunktAt: LocalDate,
    val updatedBy: String?,
    val sistVurdert: OffsetDateTime?,
    val frist: LocalDate?,
) {
    fun toAktivitetskravVurdering() = AktivitetskravVurdering(
        uuid = UUID.fromString(uuid),
        personIdent = PersonIdent(personIdent),
        createdAt = createdAt,
        status = AktivitetskravStatus.valueOf(status),
        vurdertAv = updatedBy!!,
        sistVurdert = sistVurdert!!,
    )
}
