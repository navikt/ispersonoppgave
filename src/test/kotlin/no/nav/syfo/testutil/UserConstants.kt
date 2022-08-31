package no.nav.syfo.testutil

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer

object UserConstants {
    val ARBEIDSTAKER_FNR = PersonIdent("12345678912")
    val ARBEIDSTAKER_2_FNR = PersonIdent(ARBEIDSTAKER_FNR.value.replace("2", "1"))
    val VIRKSOMHETSNUMMER = Virksomhetsnummer("123456789")
    const val NAV_ENHET = "0330"
    const val VEILEDER_IDENT = "Z999999"
}
