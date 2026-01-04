package no.nav.syfo.domain

import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.*
import java.util.*

enum class DialogmoteStatusendringType {
    INNKALT,
    AVLYST,
    FERDIGSTILT,
    NYTT_TID_STED,
    LUKKET,
}

data class DialogmoteStatusendring constructor(
    val uuid: UUID,
    val personIdent: PersonIdent,
    val type: DialogmoteStatusendringType,
    val endringTidspunkt: OffsetDateTime,
    val dialogmoteUuid: UUID,
    val veilederIdent: String,
) {

    companion object {
        fun create(kDialogmoteStatusEndring: KDialogmoteStatusEndring) = DialogmoteStatusendring(
            uuid = UUID.randomUUID(),
            personIdent = PersonIdent(kDialogmoteStatusEndring.getPersonIdent()),
            type = DialogmoteStatusendringType.valueOf(kDialogmoteStatusEndring.getStatusEndringType()),
            endringTidspunkt = OffsetDateTime.ofInstant(
                kDialogmoteStatusEndring.getStatusEndringTidspunkt(),
                ZoneId.of("Europe/Oslo").rules.getOffset(kDialogmoteStatusEndring.getStatusEndringTidspunkt())
            ),
            dialogmoteUuid = UUID.fromString(kDialogmoteStatusEndring.getDialogmoteUuid()),
            veilederIdent = kDialogmoteStatusEndring.getNavIdent(),
        )
    }
}

infix fun DialogmoteStatusendring.happenedAfter(
    personOppgave: PersonOppgave,
) = endringTidspunkt.toLocalDateTimeOslo().isAfter(personOppgave.sistEndret)

fun DialogmoteStatusendring.didFinishDialogmote() =
    type == DialogmoteStatusendringType.AVLYST ||
        type == DialogmoteStatusendringType.FERDIGSTILT ||
        type == DialogmoteStatusendringType.LUKKET
