package no.nav.syfo.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.MDC
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.util.*
import java.util.Collections.emptyMap

const val GUID = "guid"
const val TYPE = "type"
const val CREATED_DATE = "createdDate"
const val CALL_ID = "callId"
const val NAV_CALLID = "Nav-Callid"

class SyfoProducerRecord<K, V>(topic: String, key: K, value: V, headers: Map<String, Any> = emptyMap()) :
        ProducerRecord<K, V>(topic, null, System.currentTimeMillis(), key, value, defaultHeaders<V>(value, headers)) {

    fun addHeaders(headers: Map<String, Any>) {
        headers.entries
                .map { entry -> RecordHeader(entry.key, toUtf8Bytes(entry.value)) }
                .forEach { headers().add(it) }
    }

    companion object {
        private fun <V> defaultHeaders(value: V, additionalHeaders: Map<String, Any>): Iterable<Header> {
            val guid = RecordHeader(GUID, toUtf8Bytes(UUID.randomUUID()))
            val callId = RecordHeader(CALL_ID, toUtf8Bytes(MDC.get(NAV_CALLID)))
            val navCallid = RecordHeader(NAV_CALLID, toUtf8Bytes(MDC.get(NAV_CALLID)))
            val type = RecordHeader(TYPE, toUtf8Bytes((value as? Any)?.javaClass?.simpleName))
            val createdDate = RecordHeader(CREATED_DATE, toUtf8Bytes(LocalDateTime.now()))

            return ArrayList<Header>().apply {
                add(guid)
                add(callId)
                add(navCallid)
                add(type)
                add(createdDate)
                addAll(additionalHeaders.entries.map { entry -> RecordHeader(entry.key, toUtf8Bytes(entry.value)) })
            }
        }

        private fun toUtf8Bytes(any: Any?): ByteArray =
                any?.toString()?.toByteArray(UTF_8) ?: ByteArray(0)
    }
}
