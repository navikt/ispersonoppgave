package no.nav.syfo.testutil

import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VIRKSOMHETSNUMMER
import java.time.LocalDate
import java.util.*

fun generateKOppfolgingsplanLPSNAV(fodselsnummer: Fodselsnummer): KOppfolgingsplanLPSNAV {
    return KOppfolgingsplanLPSNAV(
        UUID.randomUUID().toString(),
        fodselsnummer.value,
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
