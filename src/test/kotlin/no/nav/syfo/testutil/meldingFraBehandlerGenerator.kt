package no.nav.syfo.testutil

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.meldingfrabehandler.domain.KMeldingFraBehandler
import no.nav.syfo.meldingfrabehandler.domain.MeldingType
import java.time.OffsetDateTime
import java.util.*

fun generateKMeldingFraBehandler(
    uuid: UUID = UUID.randomUUID(),
) = KMeldingFraBehandler(
    uuid = uuid.toString(),
    personIdent = UserConstants.ARBEIDSTAKER_FNR.value,
    type = MeldingType.FORESPORSEL_PASIENT.name,
    conversationRef = UUID.randomUUID().toString(),
    parentRef = null,
    msgId = null,
    tidspunkt = OffsetDateTime.now(),
    behandlerPersonIdent = PersonIdent("12312312310").value,
)
