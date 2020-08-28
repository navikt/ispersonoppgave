package no.nav.syfo.util

fun validateFnr(fnr: String): Boolean {
    return fnr.isNotEmpty() && fnr.matches("\\d{11}$".toRegex())
}
