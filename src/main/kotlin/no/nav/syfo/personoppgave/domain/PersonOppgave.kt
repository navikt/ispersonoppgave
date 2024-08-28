package no.nav.syfo.personoppgave.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.personoppgave.api.PersonOppgaveVeileder
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.util.toLocalDateTimeOslo
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

data class PersonOppgave(
    val uuid: UUID,
    val referanseUuid: UUID,
    val personIdent: PersonIdent,
    val virksomhetsnummer: Virksomhetsnummer?,
    val type: PersonOppgaveType,
    val oversikthendelseTidspunkt: LocalDateTime?,
    val behandletTidspunkt: LocalDateTime?,
    val behandletVeilederIdent: String?,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime, // Referansetidspunkt til når hendelsen som sist endret oppgaven skjedde
    val publish: Boolean,
    val publishedAt: OffsetDateTime?,
) {
    constructor(
        referanseUuid: UUID,
        personIdent: PersonIdent,
        type: PersonOppgaveType,
        publish: Boolean = false,
    ) : this(
        uuid = UUID.randomUUID(),
        referanseUuid = referanseUuid,
        personIdent = personIdent,
        virksomhetsnummer = null,
        type = type,
        oversikthendelseTidspunkt = null,
        behandletTidspunkt = null,
        behandletVeilederIdent = null,
        opprettet = LocalDateTime.now(),
        sistEndret = LocalDateTime.now(),
        publish = publish,
        publishedAt = null,
    )
}

fun PersonOppgave.toPersonOppgaveVeileder(): PersonOppgaveVeileder {
    return PersonOppgaveVeileder(
        uuid = this.uuid.toString(),
        referanseUuid = this.referanseUuid.toString(),
        fnr = this.personIdent.value,
        virksomhetsnummer = "",
        type = this.type.name,
        behandletTidspunkt = this.behandletTidspunkt,
        behandletVeilederIdent = this.behandletVeilederIdent,
        opprettet = this.opprettet,
    )
}

infix fun PersonOppgave.hasSameOppgaveTypeAs(other: PersonOppgave): Boolean = type == other.type

fun PersonOppgave.toHendelseType(): PersonoppgavehendelseType {
    return when (type) {
        PersonOppgaveType.DIALOGMOTESVAR -> {
            if (isUBehandlet())
                PersonoppgavehendelseType.DIALOGMOTESVAR_MOTTATT
            else
                PersonoppgavehendelseType.DIALOGMOTESVAR_BEHANDLET
        }
        PersonOppgaveType.OPPFOLGINGSPLANLPS -> {
            if (isUBehandlet())
                PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT
            else
                PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET
        }
        PersonOppgaveType.BEHANDLERDIALOG_SVAR -> {
            if (isUBehandlet()) PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT
            else PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_BEHANDLET
        }
        PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART -> {
            if (isUBehandlet()) PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_MOTTATT
            else PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_BEHANDLET
        }
        PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST -> {
            if (isUBehandlet()) PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_MOTTATT
            else PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_BEHANDLET
        }
        PersonOppgaveType.BEHANDLER_BER_OM_BISTAND -> {
            if (isUBehandlet()) PersonoppgavehendelseType.BEHANDLER_BER_OM_BISTAND_MOTTATT
            else PersonoppgavehendelseType.BEHANDLER_BER_OM_BISTAND_BEHANDLET
        }
    }
}

fun PersonOppgave.isBehandlet() = behandletTidspunkt != null

fun PersonOppgave.isUBehandlet() = !isBehandlet()

fun PersonOppgave.behandle(veilederIdent: String): PersonOppgave {
    return this.copy(
        behandletTidspunkt = OffsetDateTime.now().toLocalDateTimeOslo(),
        behandletVeilederIdent = veilederIdent,
    )
}

fun PersonOppgave.behandleAndReadyForPublish(veilederIdent: String): PersonOppgave {
    val now = OffsetDateTime.now().toLocalDateTimeOslo()
    return this.copy(
        behandletTidspunkt = now,
        behandletVeilederIdent = veilederIdent,
        sistEndret = now,
        publish = true,
    )
}

fun PersonOppgave.shouldPublishOppgaveHendelseNow(): Boolean {
    return type == PersonOppgaveType.OPPFOLGINGSPLANLPS ||
        type == PersonOppgaveType.BEHANDLERDIALOG_SVAR ||
        type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART ||
        type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST ||
        type == PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
}
