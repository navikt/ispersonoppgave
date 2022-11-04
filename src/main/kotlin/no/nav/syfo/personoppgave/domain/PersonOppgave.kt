package no.nav.syfo.personoppgave.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.personoppgave.api.PersonOppgaveVeileder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

data class PersonOppgave(
    val id: Int,
    val uuid: UUID,
    val referanseUuid: UUID,
    val personIdent: PersonIdent,
    val virksomhetsnummer: Virksomhetsnummer,
    val type: PersonOppgaveType,
    val oversikthendelseTidspunkt: LocalDateTime?,
    val behandletTidspunkt: LocalDateTime?,
    val behandletVeilederIdent: String?,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime, // Referansetidspunkt til når hendelsen som sist endret oppgaven skjedde
    val publish: Boolean,
    val publishedAt: OffsetDateTime?,
)

fun PersonOppgave.toPersonOppgaveVeileder(): PersonOppgaveVeileder {
    return PersonOppgaveVeileder(
        uuid = this.uuid.toString(),
        referanseUuid = this.referanseUuid.toString(),
        fnr = this.personIdent.value,
        virksomhetsnummer = this.virksomhetsnummer.value,
        type = this.type.name,
        behandletTidspunkt = this.behandletTidspunkt,
        behandletVeilederIdent = this.behandletVeilederIdent,
        opprettet = this.opprettet,
    )
}
