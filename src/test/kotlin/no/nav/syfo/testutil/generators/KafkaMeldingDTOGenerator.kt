package no.nav.syfo.testutil.generators

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.kafka.behandlerdialog.KMeldingDTO
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.testutil.UserConstants
import java.time.OffsetDateTime
import java.util.*

fun generateKMeldingDTO(
    uuid: UUID = UUID.randomUUID(),
    parentRef: UUID? = null,
) = KMeldingDTO(
    uuid = uuid.toString(),
    personIdent = UserConstants.ARBEIDSTAKER_FNR.value,
    type = MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER.name,
    conversationRef = UUID.randomUUID().toString(),
    parentRef = parentRef?.toString(),
    msgId = null,
    tidspunkt = OffsetDateTime.now(),
    behandlerPersonIdent = PersonIdent("12312312310").value,
)
