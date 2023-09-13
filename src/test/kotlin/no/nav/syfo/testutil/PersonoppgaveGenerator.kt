package no.nav.syfo.testutil

import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.time.LocalDateTime
import java.util.*

fun generatePersonoppgave(
    uuid: UUID = UUID.randomUUID(),
    referanseUuid: UUID = UUID.randomUUID(),
    type: PersonOppgaveType = PersonOppgaveType.DIALOGMOTESVAR,
    virksomhetsnummer: Virksomhetsnummer? = null,
) = PersonOppgave(
    id = 1,
    uuid = uuid,
    referanseUuid = referanseUuid,
    personIdent = UserConstants.ARBEIDSTAKER_FNR,
    virksomhetsnummer = virksomhetsnummer,
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
