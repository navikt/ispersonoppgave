package no.nav.syfo.testutil.generators

import no.nav.syfo.arbeidsuforhet.kafka.ArbeidsuforhetVurdering
import no.nav.syfo.arbeidsuforhet.kafka.VurderingType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testutil.UserConstants
import java.time.OffsetDateTime
import java.util.*

fun generateArbeidsuforhetVurdering(
    type: VurderingType,
    personident: PersonIdent = UserConstants.ARBEIDSTAKER_FNR,
    createdAt: OffsetDateTime = OffsetDateTime.now(),
) = ArbeidsuforhetVurdering(
    uuid = UUID.randomUUID(),
    personident = personident.value,
    createdAt = createdAt,
    veilederident = UserConstants.VEILEDER_IDENT,
    type = type,
    begrunnelse = "En begrunnelse"
)
