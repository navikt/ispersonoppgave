package no.nav.syfo.testutil

import no.nav.syfo.personoppgave.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.Virksomhetsnummer

object UserConstants {
    val ARBEIDSTAKER_FNR = PersonIdent("12345678912")
    val ARBEIDSTAKER_2_FNR = PersonIdent(ARBEIDSTAKER_FNR.value.replace("2", "1"))
    val ARBEIDSTAKER_3_FNR = PersonIdent("12345678913")
    val FEILENDE_FNR = PersonIdent("12345670000")
    val VIRKSOMHETSNUMMER = Virksomhetsnummer("123456789")
    const val NAV_ENHET = "0330"
    const val VEILEDER_IDENT = "Z999999"
}
