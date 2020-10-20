package no.nav.syfo.oversikthendelse.retry

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.kafka.SyfoProducerRecord
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.util.callIdArgument
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

const val OVERSIKTHENDELSE_RETRY_TOPIC = "privat-ispersonoppgave-oversikthendelse-retry-v1"

class OversikthendelseRetryProducer(
    private val producer: KafkaProducer<String, KOversikthendelseRetry>
) {
    fun sendFirstOversikthendelseRetry(
        fnr: Fodselsnummer,
        oversikthendelseType: OversikthendelseType,
        personOppgaveId: Int,
        callId: String = ""
    ) {
        val now = LocalDateTime.now()
        val firstKOversikthendelseRetry = KOversikthendelseRetry(
            created = now,
            retryTime = now.plusMinutes(RETRY_OVERSIKTHENDELSE_INTERVAL_MINUTES),
            retriedCount = 0,
            fnr = fnr.value,
            oversikthendelseType = oversikthendelseType.name,
            personOppgaveId = personOppgaveId
        )
        producer.send(producerRecord(firstKOversikthendelseRetry))
        log.warn(
            "Sent first OversikthendelseRetry: {}, {}, {}, {}",
            StructuredArguments.keyValue("oversikthendelseType", firstKOversikthendelseRetry.oversikthendelseType)!!,
            StructuredArguments.keyValue("retriedCount", firstKOversikthendelseRetry.retriedCount)!!,
            StructuredArguments.keyValue("retryTime", firstKOversikthendelseRetry.retryTime)!!,
            callIdArgument(callId)
        )
    }

    fun sendRetriedOversikthendelseRetry(
        kOversikthendelseRetry: KOversikthendelseRetry,
        callId: String = ""
    ) {
        val now = LocalDateTime.now()
        val newRetryCounter = kOversikthendelseRetry.retriedCount.plus(1)
        val newKOversikthendelseRetry = kOversikthendelseRetry.copy(
            created = now,
            retryTime = now.plusMinutes(RETRY_OVERSIKTHENDELSE_INTERVAL_MINUTES),
            retriedCount = newRetryCounter
        )
        producer.send(producerRecord(newKOversikthendelseRetry))
        log.warn(
            "Sent OversikthendelseRetry: {}, {}, {}, {}",
            StructuredArguments.keyValue("oversikthendelseType", newKOversikthendelseRetry.oversikthendelseType)!!,
            StructuredArguments.keyValue("retriedCount", newKOversikthendelseRetry.retriedCount)!!,
            StructuredArguments.keyValue("retryTime", newKOversikthendelseRetry.retryTime)!!,
            callIdArgument(callId)
        )
    }

    fun sendAgainOversikthendelseRetry(
        kOversikthendelseRetry: KOversikthendelseRetry,
        callId: String = ""
    ) {
        producer.send(producerRecord(kOversikthendelseRetry))
        log.info(
            "Sent OversikthendelseRetry again: {}, {}. {}, {}",
            StructuredArguments.keyValue("oversikthendelseType", kOversikthendelseRetry.oversikthendelseType)!!,
            StructuredArguments.keyValue("retriedCount", kOversikthendelseRetry.retriedCount)!!,
            StructuredArguments.keyValue("retryTime", kOversikthendelseRetry.retryTime)!!,
            callIdArgument(callId)
        )
    }

    private fun producerRecord(oversikthendelseRetry: KOversikthendelseRetry) =
        SyfoProducerRecord(
            topic = OVERSIKTHENDELSE_RETRY_TOPIC,
            key = UUID.randomUUID().toString(),
            value = oversikthendelseRetry
        )

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OversikthendelseRetryProducer::class.java)
    }
}
