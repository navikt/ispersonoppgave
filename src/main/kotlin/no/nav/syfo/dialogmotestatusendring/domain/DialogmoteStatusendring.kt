package no.nav.syfo.dialogmotestatusendring.domain

import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

enum class DialogmoteStatusendringType {
    INNKALT,
    AVLYST,
    FERDIGSTILT,
    NYTT_TID_STED,
    LUKKET,
}

data class DialogmoteStatusendring constructor(
    val personIdent: PersonIdent,
    val type: DialogmoteStatusendringType,
    val endringTidspunkt: OffsetDateTime,
    val dialogmoteUuid: UUID,
    val veilederIdent: String,
) {

    companion object {
        fun create(kDialogmoteStatusEndring: KDialogmoteStatusEndring) = DialogmoteStatusendring(
            personIdent = PersonIdent(kDialogmoteStatusEndring.getPersonIdent()),
            type = DialogmoteStatusendringType.valueOf(kDialogmoteStatusEndring.getStatusEndringType()),
            endringTidspunkt = OffsetDateTime.ofInstant(
                kDialogmoteStatusEndring.getStatusEndringTidspunkt(),
                ZoneOffset.UTC
            ),
            dialogmoteUuid = UUID.fromString(kDialogmoteStatusEndring.getDialogmoteUuid()),
            veilederIdent = kDialogmoteStatusEndring.getNavIdent(),
        )
    }
}

infix fun DialogmoteStatusendring.happenedAfter(
    personOppgave: PersonOppgave,
) = endringTidspunkt.toLocalDateTimeOslo().isAfter(personOppgave.sistEndret)
