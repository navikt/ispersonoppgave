package no.nav.syfo.testutil

import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VIRKSOMHETSNUMMER
import java.time.LocalDate
import java.util.*

val generateKOppfolgingsplanLPSNAV =
    KOppfolgingsplanLPSNAV(
        UUID.randomUUID().toString(),
        ARBEIDSTAKER_FNR.value,
        VIRKSOMHETSNUMMER.value,
        true,
        LocalDate.now().toEpochDay().toInt()
    )
