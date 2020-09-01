package no.nav.syfo.oversikthendelse

import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.kafka.SyfoProducerRecord
import no.nav.syfo.metric.COUNT_OPPFOLGINGSTILFELLE_SKIPPED_BEHANDLENDEENHET
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

val log = LoggerFactory.getLogger("no.nav.syfo.oversikthendelse")

const val OVERSIKTHENDELSE_TOPIC = "aapen-syfo-oversikthendelse-v1"

class OversikthendelseProducer(
    private val producer: KafkaProducer<String, KOversikthendelse>,
    private val behandlendeEnhetClient: BehandlendeEnhetClient
) {
    fun sendOversikthendelse(
        fnr: Fodselsnummer,
        oversikthendelseType: OversikthendelseType,
        callId: String = ""
    ) {
        val behandlendeEnhet = behandlendeEnhetClient.getEnhet(fnr, callId)
            ?: return skipOppfolgingstilfelleWithMissingValue(MissingValue.BEHANDLENDEENHET)

        val kOversikthendelse = KOversikthendelse(
            fnr = fnr.value,
            hendelseId = oversikthendelseType.name,
            enhetId = behandlendeEnhet.enhetId,
            tidspunkt = LocalDateTime.now()
        )
        producer.send(producerRecord(kOversikthendelse))
    }
}

private fun producerRecord(oversikthendelse: KOversikthendelse) =
    SyfoProducerRecord(
        topic = OVERSIKTHENDELSE_TOPIC,
        key = UUID.randomUUID().toString(),
        value = oversikthendelse
    )

enum class MissingValue {
    BEHANDLENDEENHET
}

private fun skipOppfolgingstilfelleWithMissingValue(missingValue: MissingValue) {
    when (missingValue) {
        MissingValue.BEHANDLENDEENHET -> COUNT_OPPFOLGINGSTILFELLE_SKIPPED_BEHANDLENDEENHET.inc()
    }
}
