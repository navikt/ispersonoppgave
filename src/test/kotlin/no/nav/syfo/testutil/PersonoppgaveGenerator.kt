package no.nav.syfo.testutil

import no.nav.syfo.personoppgave.domain.PPersonOppgave
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

fun generatePPersonoppgave(
    referanseUuid: UUID = UUID.randomUUID(),
    sistEndret: LocalDateTime = LocalDateTime.now(),
) =
    PPersonOppgave(
        id = 1,
        uuid = UUID.randomUUID(),
        referanseUuid = referanseUuid,
        fnr = UserConstants.ARBEIDSTAKER_FNR.value,
        virksomhetsnummer = "",
        type = PersonOppgaveType.DIALOGMOTESVAR.name,
        oversikthendelseTidspunkt = null,
        behandletTidspunkt = null,
        behandletVeilederIdent = null,
        opprettet = LocalDateTime.now(),
        sistEndret = sistEndret,
        publish = false,
        publishedAt = null,
    )

fun generatePPersonoppgaver() = listOf(generatePPersonoppgave())
