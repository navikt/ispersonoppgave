package no.nav.syfo.arbeidsuforhet.kafka

import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class ExpiredForhandsvarsel(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personident: PersonIdent,
    val svarfrist: LocalDate,
)
