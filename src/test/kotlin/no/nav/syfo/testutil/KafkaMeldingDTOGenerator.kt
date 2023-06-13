package no.nav.syfo.testutil

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.domain.MeldingType
import java.time.OffsetDateTime
import java.util.*

fun generateKMeldingDTO(
    uuid: UUID = UUID.randomUUID(),
    parentRef: UUID? = null,
) = KMeldingDTO(
    uuid = uuid.toString(),
    personIdent = UserConstants.ARBEIDSTAKER_FNR.value,
    type = MeldingType.FORESPORSEL_PASIENT.name,
    conversationRef = UUID.randomUUID().toString(),
    parentRef = parentRef?.toString(),
    msgId = null,
    tidspunkt = OffsetDateTime.now(),
    behandlerPersonIdent = PersonIdent("12312312310").value,
)
