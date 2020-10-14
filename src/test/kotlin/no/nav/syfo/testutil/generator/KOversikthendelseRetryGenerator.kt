package no.nav.syfo.testutil.generator

import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.oversikthendelse.retry.KOversikthendelseRetry
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import java.time.LocalDateTime

val generateKOversikthendelseRetry =
    KOversikthendelseRetry(
        created = LocalDateTime.now(),
        retryTime = LocalDateTime.now(),
        retriedCount = 0,
        fnr = ARBEIDSTAKER_FNR.value,
        oversikthendelseType = OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name,
        personOppgaveId = 1,
    ).copy()
