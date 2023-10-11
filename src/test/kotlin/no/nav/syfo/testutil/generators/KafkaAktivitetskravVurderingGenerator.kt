package no.nav.syfo.testutil.generators

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.testutil.UserConstants
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

fun generateKafkaAktivitetskravVurdering(
    uuid: String = UUID.randomUUID().toString(),
    personIdent: String = UserConstants.ARBEIDSTAKER_FNR.value,
    status: String = AktivitetskravStatus.UNNTAK.name,
    updatedBy: String? = UserConstants.VEILEDER_IDENT,
    sistVurdert: OffsetDateTime? = OffsetDateTime.now(),
) = KafkaAktivitetskravVurdering(
    uuid = uuid,
    personIdent = personIdent,
    createdAt = OffsetDateTime.now(),
    status = status,
    beskrivelse = null,
    arsaker = emptyList(),
    stoppunktAt = LocalDate.now().plusDays(10),
    updatedBy = updatedBy,
    sistVurdert = sistVurdert,
    frist = null,
)
