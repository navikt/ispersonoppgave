package no.nav.syfo.dialogmotesvar.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.OffsetDateTime
import java.util.*

data class Dialogmotesvar(
    val moteuuid: UUID,
    val arbeidstakerIdent: PersonIdent,
    val svarType: DialogmoteSvartype,
    val senderType: SenderType,
    val brevSentAt: OffsetDateTime,
    val svarReceivedAt: OffsetDateTime,
)

fun Dialogmotesvar.isRelevantToVeileder() =
    svarType == DialogmoteSvartype.KOMMER_IKKE || svarType == DialogmoteSvartype.NYTT_TID_STED

fun Dialogmotesvar.isNotRelevantToVeileder() = !isRelevantToVeileder()

infix fun Dialogmotesvar.happenedAfter(
    personOppgave: PersonOppgave,
) = svarReceivedAt.toLocalDateTimeOslo().isAfter(personOppgave.sistEndret)
