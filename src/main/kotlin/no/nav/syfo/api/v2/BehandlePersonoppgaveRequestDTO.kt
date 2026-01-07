package no.nav.syfo.api.v2

import no.nav.syfo.domain.PersonOppgaveType

data class BehandlePersonoppgaveRequestDTO(
    val personIdent: String,
    val personOppgaveType: PersonOppgaveType,
)
