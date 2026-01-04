package no.nav.syfo.infrastructure.kafka.oppfolgingsplanlps

data class KOppfolgingsplanLPS(
    val uuid: String,
    val fodselsnummer: String,
    val virksomhetsnummer: String,
    val behovForBistandFraNav: Boolean,
    val opprettet: Int,
)
