package no.nav.syfo.testutil.generators

import no.nav.syfo.arbeidsuforhet.kafka.ExpiredForhandsvarsel
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testutil.UserConstants
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun generateExpiredForhandsvarsel(
    personident: PersonIdent = PersonIdent(UserConstants.ARBEIDSTAKER_FNR.value),
) = ExpiredForhandsvarsel(
    uuid = UUID.randomUUID(),
    personIdent = personident,
    createdAt = OffsetDateTime.now(),
    svarfrist = LocalDate.now().plusWeeks(3),
)
