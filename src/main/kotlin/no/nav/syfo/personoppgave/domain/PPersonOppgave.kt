package no.nav.syfo.personoppgave.domain

import java.time.LocalDateTime
import java.util.*

data class PPersonOppgave(
    val id: Int,
    val uuid: UUID,
    val referanseUuid: UUID,
    val fnr: String,
    val virksomhetsnummer: String,
    val type: String,
    val oversikthendelseTidspunkt: LocalDateTime?,
    val behandletTidspunkt: LocalDateTime?,
    val behandletVeilederIdent: String?,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime
)
