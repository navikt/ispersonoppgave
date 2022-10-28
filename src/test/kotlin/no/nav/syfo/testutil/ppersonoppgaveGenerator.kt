package no.nav.syfo.testutil

import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import java.time.LocalDateTime
import java.util.*

fun generatePPersonoppgave(
    moteuuid: UUID = UUID.randomUUID(),
    sistEndret: LocalDateTime = LocalDateTime.now(),
) =
    PPersonOppgave(
        id = 1,
        uuid = UUID.randomUUID(),
        referanseUuid = moteuuid,
        fnr = UserConstants.ARBEIDSTAKER_FNR.value,
        virksomhetsnummer = UserConstants.VIRKSOMHETSNUMMER.value,
        type = PersonOppgaveType.DIALOGMOTESVAR.name,
        oversikthendelseTidspunkt = null,
        behandletTidspunkt = null,
        behandletVeilederIdent = null,
        opprettet = LocalDateTime.now(),
        sistEndret = sistEndret,
        publish = false,
        publishedAt = null,
    )
