package no.nav.syfo.behandlerdialog.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class Melding(
    val referanseUuid: UUID,
    val personIdent: PersonIdent,
    val type: MeldingType,
    val tidspunkt: OffsetDateTime,
)

enum class MeldingType {
    FORESPORSEL_PASIENT,
    FORESPORSEL_PASIENT_PAMINNELSE,
}