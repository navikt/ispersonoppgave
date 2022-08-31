package no.nav.syfo.testutil

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VIRKSOMHETSNUMMER
import java.time.LocalDate
import java.util.*

fun generateKOppfolgingsplanLPSNAV(personIdent: PersonIdent): KOppfolgingsplanLPSNAV {
    return KOppfolgingsplanLPSNAV(
        UUID.randomUUID().toString(),
        personIdent.value,
        VIRKSOMHETSNUMMER.value,
        true,
        LocalDate.now().toEpochDay().toInt()
    )
}

val generateKOppfolgingsplanLPSNAV =
    KOppfolgingsplanLPSNAV(
        UUID.randomUUID().toString(),
        ARBEIDSTAKER_FNR.value,
        VIRKSOMHETSNUMMER.value,
        true,
        LocalDate.now().toEpochDay().toInt()
    )

val generateKOppfolgingsplanLPSNAV2 =
    KOppfolgingsplanLPSNAV(
        UUID.randomUUID().toString(),
        ARBEIDSTAKER_FNR.value,
        VIRKSOMHETSNUMMER.value,
        true,
        LocalDate.now().toEpochDay().toInt()
    )

val generateKOppfolgingsplanLPSNAVNoBehovforForBistand =
    KOppfolgingsplanLPSNAV(
        UUID.randomUUID().toString(),
        ARBEIDSTAKER_FNR.value,
        VIRKSOMHETSNUMMER.value,
        false,
        LocalDate.now().toEpochDay().toInt()
    )
