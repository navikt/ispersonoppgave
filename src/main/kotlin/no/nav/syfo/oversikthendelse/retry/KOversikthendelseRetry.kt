package no.nav.syfo.oversikthendelse.retry

import java.time.LocalDateTime

const val RETRY_OVERSIKTHENDELSE_LIMIT_HOURS = 24
const val RETRY_OVERSIKTHENDELSE_INTERVAL_MINUTES = 15L
const val RETRY_OVERSIKTHENDELSE_COUNT_LIMIT = (RETRY_OVERSIKTHENDELSE_LIMIT_HOURS / (RETRY_OVERSIKTHENDELSE_INTERVAL_MINUTES / 60.0)).toInt()

data class KOversikthendelseRetry(
    val created: LocalDateTime,
    val retryTime: LocalDateTime,
    val retriedCount: Int,
    val fnr: String,
    val oversikthendelseType: String,
    val personOppgaveId: Int
)

fun KOversikthendelseRetry.hasExceededRetryLimit(): Boolean {
    return this.retriedCount >= RETRY_OVERSIKTHENDELSE_COUNT_LIMIT
}

fun KOversikthendelseRetry.isReadyToRetry(): Boolean {
    return LocalDateTime.now().isAfter(retryTime)
}
