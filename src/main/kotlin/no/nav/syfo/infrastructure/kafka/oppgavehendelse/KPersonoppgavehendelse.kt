package no.nav.syfo.infrastructure.kafka.oppgavehendelse

data class KPersonoppgavehendelse(
    val personident: String,
    val hendelsetype: String,
)
