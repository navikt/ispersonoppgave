package no.nav.syfo.util

import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import java.util.*

fun PipelineContext<out Unit, ApplicationCall>.personIdentHeader(): String? {
    return this.call.request.headers[NAV_PERSONIDENT_HEADER.lowercase(Locale.getDefault())]
}
