package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

private val kafkaCounter = AtomicInteger(0)

fun kafkaCallId(): String = "${
LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-HHmm"))
}-ispersonoppgave-kafka-${kafkaCounter.incrementAndGet()}"
