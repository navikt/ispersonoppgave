package no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka

data class KOppfolgingsplanLPSNAV(
    val uuid: String,
    val fodselsnummer: String,
    val virksomhetsnummer: String,
    val behovForBistandFraNav: Boolean,
    val opprettet: Int
)
