package no.nav.syfo.behandlerdialog.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class KMeldingDTO(
    val uuid: String,
    val personIdent: String,
    val type: String,
    val conversationRef: String,
    val parentRef: String?,
    val msgId: String?,
    val tidspunkt: OffsetDateTime,
    val behandlerPersonIdent: String?,
)

fun KMeldingDTO.toMelding(): Melding = Melding(
    referanseUuid = UUID.fromString(uuid),
    personIdent = PersonIdent(personIdent),
    type = MeldingType.valueOf(type),
    tidspunkt = tidspunkt,
)
