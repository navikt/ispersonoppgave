package no.nav.syfo.personoppgave.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.LocalDateTime
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
    val sistEndret: LocalDateTime
)

fun PPersonOppgave.toPersonOppgave(): PersonOppgave {
    return PersonOppgave(
        id = this.id,
        uuid = this.uuid,
        referanseUuid = this.referanseUuid,
        personIdentNumber = PersonIdentNumber(this.fnr),
        virksomhetsnummer = Virksomhetsnummer(this.virksomhetsnummer),
        type = PersonOppgaveType.valueOf(this.type),
        oversikthendelseTidspunkt = this.oversikthendelseTidspunkt,
        behandletTidspunkt = this.behandletTidspunkt,
        behandletVeilederIdent = this.behandletVeilederIdent,
        opprettet = this.opprettet,
        sistEndret = this.sistEndret
    )
}
