package no.nav.syfo.personoppgave.api

import java.time.LocalDateTime
import java.util.*

data class PersonOppgaveVeileder(
    val uuid: String,
    val referanseUuid: String,
    val fnr: String,
    val virksomhetsnummer: String,
    val type: String,
    val behandletTidspunkt: LocalDateTime?,
    val behandletVeilederIdent: String?,
    val opprettet: LocalDateTime
)
