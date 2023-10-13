package no.nav.syfo.testutil.generators

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.UserConstants
import java.time.LocalDateTime
import java.util.*

fun generatePersonoppgave(
    uuid: UUID = UUID.randomUUID(),
    referanseUuid: UUID = UUID.randomUUID(),
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_FNR,
    type: PersonOppgaveType = PersonOppgaveType.DIALOGMOTESVAR,
    virksomhetsnummer: Virksomhetsnummer? = null,
    behandletTidspunkt: LocalDateTime? = null,
    behandletVeilederIdent: String? = null,
    opprettet: LocalDateTime = LocalDateTime.now(),
    sistEndret: LocalDateTime = LocalDateTime.now(),
    publish: Boolean = false,
) = PersonOppgave(
    uuid = uuid,
    referanseUuid = referanseUuid,
    personIdent = personIdent,
    virksomhetsnummer = virksomhetsnummer,
    type = type,
    oversikthendelseTidspunkt = null,
    behandletTidspunkt = behandletTidspunkt,
    behandletVeilederIdent = behandletVeilederIdent,
    opprettet = opprettet,
    sistEndret = sistEndret,
    publish = publish,
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
