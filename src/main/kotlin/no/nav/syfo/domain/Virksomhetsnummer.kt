package no.nav.syfo.domain

data class Virksomhetsnummer(val value: String) {
    private val elevenDigits = Regex("\\d{9}")

    init {
        if (!elevenDigits.matches(value)) {
            throw IllegalArgumentException("$value is not a valid Virksomhetsnummer")
        }
    }
}
