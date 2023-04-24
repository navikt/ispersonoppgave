package no.nav.syfo.meldingfrabehandler.domain

import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class MeldingFraBehandler(
    val referanseUuid: UUID,
    val personIdent: PersonIdent,
    val type: MeldingType,
    val tidspunkt: OffsetDateTime,
)

enum class MeldingType {
    FORESPORSEL_PASIENT,
}
