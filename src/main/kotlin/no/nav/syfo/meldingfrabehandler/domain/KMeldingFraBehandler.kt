package no.nav.syfo.meldingfrabehandler.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class KMeldingFraBehandler(
    val uuid: String,
    val personIdent: String,
    val type: String,
    val conversationRef: String,
    val parentRef: String?,
    val msgId: String?,
    val tidspunkt: OffsetDateTime,
    val behandlerPersonIdent: String?,
)

fun KMeldingFraBehandler.toMeldingFraBehandler(): MeldingFraBehandler = MeldingFraBehandler(
    referanseUuid = UUID.fromString(uuid),
    personIdent = PersonIdent(personIdent),
    type = MeldingType.valueOf(type),
    tidspunkt = tidspunkt,
)
