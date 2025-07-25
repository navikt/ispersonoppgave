package no.nav.syfo.personoppgave.domain

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

data class PPersonOppgave(
    val id: Int,
    val uuid: UUID,
    val referanseUuid: UUID,
    val fnr: String,
    val virksomhetsnummer: String,
    val type: String,
    val oversikthendelseTidspunkt: LocalDateTime?,
    val behandletTidspunkt: LocalDateTime?,
    val behandletVeilederIdent: String?,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime,
    val publish: Boolean,
    val publishedAt: OffsetDateTime?,
    val duplikatReferanseUuid: UUID?,
)

fun PPersonOppgave.toPersonOppgave(): PersonOppgave {
    return PersonOppgave(
        uuid = this.uuid,
        referanseUuid = this.referanseUuid,
        personIdent = PersonIdent(this.fnr),
        virksomhetsnummer = null,
        type = PersonOppgaveType.valueOf(this.type),
        oversikthendelseTidspunkt = this.oversikthendelseTidspunkt,
        behandletTidspunkt = this.behandletTidspunkt,
        behandletVeilederIdent = this.behandletVeilederIdent,
        opprettet = this.opprettet,
        sistEndret = this.sistEndret,
        publish = this.publish,
        publishedAt = this.publishedAt,
        duplikatReferanseUuid = this.duplikatReferanseUuid,
    )
}

fun List<PPersonOppgave>.toPersonOppgaver(): List<PersonOppgave> = map { it.toPersonOppgave() }
