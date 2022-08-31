package no.nav.syfo.domain

private val elevenDigits = Regex("\\d{11}")

data class PersonIdent(val value: String) {
    init {
        if (!elevenDigits.matches(value)) {
            throw IllegalArgumentException("$value is not a valid fnr")
        }
    }
}
