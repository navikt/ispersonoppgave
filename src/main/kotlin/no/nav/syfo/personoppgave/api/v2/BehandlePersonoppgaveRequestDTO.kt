package no.nav.syfo.personoppgave.api.v2

import no.nav.syfo.personoppgave.domain.PersonOppgaveType

data class BehandlePersonoppgaveRequestDTO(
    val personIdent: String,
    val personOppgaveType: PersonOppgaveType,
)
