package no.nav.syfo.behandlerdialog.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class Melding(
    val referanseUuid: UUID,
    val personIdent: PersonIdent,
    val type: MeldingType,
    val tidspunkt: OffsetDateTime,
    val parentRef: UUID?
)

enum class MeldingType {
    FORESPORSEL_PASIENT, // TODO: Remove this after migrate isbehandlerdialog
    FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER,
    FORESPORSEL_PASIENT_PAMINNELSE,
}
