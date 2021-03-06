package no.nav.syfo.oversikthendelse

import no.nav.syfo.client.enhet.BehandlendeEnhet
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.kafka.SyfoProducerRecord
import no.nav.syfo.oversikthendelse.domain.KOversikthendelse
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

val log = LoggerFactory.getLogger("no.nav.syfo.oversikthendelse")

const val OVERSIKTHENDELSE_TOPIC = "aapen-syfo-oversikthendelse-v1"

class OversikthendelseProducer(
    private val producer: KafkaProducer<String, KOversikthendelse>
) {
    fun sendOversikthendelse(
        key: UUID,
        personIdentNumber: PersonIdentNumber,
        behandlendeEnhet: BehandlendeEnhet,
        oversikthendelseType: OversikthendelseType,
        callId: String = ""
    ) {
        val kOversikthendelse = KOversikthendelse(
            fnr = personIdentNumber.value,
            hendelseId = oversikthendelseType.name,
            enhetId = behandlendeEnhet.enhetId,
            tidspunkt = LocalDateTime.now()
        )
        producer.send(producerRecord(key, kOversikthendelse))
    }
}

private fun producerRecord(key: UUID, oversikthendelse: KOversikthendelse) =
    SyfoProducerRecord(
        topic = OVERSIKTHENDELSE_TOPIC,
        key = key.toString(),
        value = oversikthendelse
    )
