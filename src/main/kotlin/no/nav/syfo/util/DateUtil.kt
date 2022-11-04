package no.nav.syfo.util

import java.sql.Timestamp
import java.time.*

fun convert(timestamp: Timestamp): LocalDateTime =
    timestamp.toLocalDateTime()

fun convertNullable(timestamp: Timestamp?): LocalDateTime? =
    timestamp?.toLocalDateTime()

fun OffsetDateTime.toLocalDateTimeOslo(): LocalDateTime = this.atZoneSameInstant(
    ZoneId.of("Europe/Oslo")
).toLocalDateTime()

fun LocalDateTime.toOffsetDateTimeUTC(): OffsetDateTime =
    this.atZone(ZoneId.of("Europe/Oslo")).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
