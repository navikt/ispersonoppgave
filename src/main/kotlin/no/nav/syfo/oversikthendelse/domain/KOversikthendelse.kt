package no.nav.syfo.oversikthendelse.domain

import java.time.LocalDateTime

data class KOversikthendelse(
    val fnr: String,
    val hendelseId: String,
    val enhetId: String,
    val tidspunkt: LocalDateTime
)
