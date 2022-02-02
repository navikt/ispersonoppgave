package no.nav.syfo.api

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.metric.METRICS_REGISTRY

const val podMetricsPath = "/prometheus"

fun Routing.registerMetricApi() {
    get(podMetricsPath) {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
