package no.nav.syfo.testutil

import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.time.LocalDateTime
import java.util.*

fun generatePersonoppgave() = PersonOppgave(
    id = 1,
    uuid = UUID.randomUUID(),
    referanseUuid = UUID.randomUUID(),
    personIdent = UserConstants.ARBEIDSTAKER_FNR,
    virksomhetsnummer = null,
    type = PersonOppgaveType.DIALOGMOTESVAR,
    oversikthendelseTidspunkt = null,
    behandletTidspunkt = null,
    behandletVeilederIdent = null,
    opprettet = LocalDateTime.now(),
    sistEndret = LocalDateTime.now(),
    publish = false,
    publishedAt = null,
)
