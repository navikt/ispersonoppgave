package no.nav.syfo.testutil

import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.time.LocalDateTime
import java.util.*

fun generatePersonoppgave(
    type: PersonOppgaveType = PersonOppgaveType.DIALOGMOTESVAR
) = PersonOppgave(
    id = 1,
    uuid = UUID.randomUUID(),
    referanseUuid = UUID.randomUUID(),
    personIdent = UserConstants.ARBEIDSTAKER_FNR,
    virksomhetsnummer = null,
    type = type,
    oversikthendelseTidspunkt = null,
    behandletTidspunkt = null,
    behandletVeilederIdent = null,
    opprettet = LocalDateTime.now(),
    sistEndret = LocalDateTime.now(),
    publish = false,
    publishedAt = null,
)
