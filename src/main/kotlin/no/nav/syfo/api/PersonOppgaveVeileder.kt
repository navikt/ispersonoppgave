package no.nav.syfo.api

import java.time.LocalDateTime
import java.util.UUID

data class PersonOppgaveVeileder(
    val uuid: String,
    val referanseUuid: String,
    val fnr: String,
    val virksomhetsnummer: String,
    val type: String,
    val behandletTidspunkt: LocalDateTime?,
    val behandletVeilederIdent: String?,
    val opprettet: LocalDateTime,
    val duplikatReferanseUuid: UUID?,
)
