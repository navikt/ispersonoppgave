package no.nav.syfo.dialogmotesvar.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class Dialogmotesvar(
    val uuid: UUID,
    val moteuuid: UUID,
    val arbeidstakerIdent: PersonIdent,
    val svarType: DialogmoteSvartype,
    val senderType: SenderType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

fun Dialogmotesvar.isRelevantToVeileder() =
    svarType == DialogmoteSvartype.KOMMER_IKKE || svarType == DialogmoteSvartype.NYTT_TID_STED

infix fun Dialogmotesvar.happenedAfter(
    date: LocalDate,
) = LocalDate.from(svarReceivedAt).isAfter(date)

infix fun Dialogmotesvar.happenedAfter(
    personOppgave: PersonOppgave,
) = svarReceivedAt.toLocalDateTimeOslo().isAfter(personOppgave.sistEndret)
