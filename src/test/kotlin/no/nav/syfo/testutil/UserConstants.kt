package no.nav.syfo.testutil

import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.domain.Virksomhetsnummer

object UserConstants {
    val ARBEIDSTAKER_FNR = Fodselsnummer("12345678912")
    val ARBEIDSTAKER_2_FNR = Fodselsnummer(ARBEIDSTAKER_FNR.value.replace("2", "1"))
    val VIRKSOMHETSNUMMER = Virksomhetsnummer("123456789")
    const val NAV_ENHET = "0330"
}
