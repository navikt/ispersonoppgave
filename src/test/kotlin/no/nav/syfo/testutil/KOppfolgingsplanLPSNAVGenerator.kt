package no.nav.syfo.testutil

import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.KOppfolgingsplanLPS
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VIRKSOMHETSNUMMER
import java.time.LocalDate
import java.util.*

val generateKOppfolgingsplanLPS =
    KOppfolgingsplanLPS(
        UUID.randomUUID().toString(),
        ARBEIDSTAKER_FNR.value,
        VIRKSOMHETSNUMMER.value,
        true,
        LocalDate.now().toEpochDay().toInt()
    )

val generateKOppfolgingsplanLPS2 =
    KOppfolgingsplanLPS(
        UUID.randomUUID().toString(),
        ARBEIDSTAKER_FNR.value,
        VIRKSOMHETSNUMMER.value,
        true,
        LocalDate.now().toEpochDay().toInt()
    )

val generateKOppfolgingsplanLPSNoBehovforForBistand =
    KOppfolgingsplanLPS(
        UUID.randomUUID().toString(),
        ARBEIDSTAKER_FNR.value,
        VIRKSOMHETSNUMMER.value,
        false,
        LocalDate.now().toEpochDay().toInt()
    )
