package no.nav.syfo.testutil

import no.nav.syfo.aktivitetskrav.domain.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.domain.VarselType
import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.util.*

fun generateExpiredVarsel(
    personIdent: PersonIdent = PersonIdent(UserConstants.ARBEIDSTAKER_FNR.value),
    varselType: VarselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER
) = ExpiredVarsel(
    aktivitetskravUuid = UUID.randomUUID(),
    varselUuid = UUID.randomUUID(),
    createdAt = LocalDate.now().atStartOfDay(),
    personIdent = personIdent,
    varselType = varselType,
    svarfrist = LocalDate.now(),
)
