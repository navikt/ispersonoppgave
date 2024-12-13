package no.nav.syfo.util

import io.ktor.server.routing.*
import java.util.*

fun RoutingContext.personIdentHeader(): String? {
    return this.call.request.headers[NAV_PERSONIDENT_HEADER.lowercase(Locale.getDefault())]
}
