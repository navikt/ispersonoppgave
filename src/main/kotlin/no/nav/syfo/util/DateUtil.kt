package no.nav.syfo.util

import java.sql.Timestamp
import java.time.LocalDateTime

fun convert(timestamp: Timestamp): LocalDateTime =
    timestamp.toLocalDateTime()

fun convertNullable(timestamp: Timestamp?): LocalDateTime? =
    timestamp?.toLocalDateTime()
