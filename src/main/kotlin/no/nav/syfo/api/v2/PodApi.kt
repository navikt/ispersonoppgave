package no.nav.syfo.api.v2

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.database.DatabaseInterface

const val podLivenessPath = "/is_alive"
const val podReadinessPath = "/is_ready"

fun Routing.registerPodApi(
    applicationState: ApplicationState,
    database: DatabaseInterface,
) {
    get(podLivenessPath) {
        if (applicationState.alive) {
            call.respondText(
                text = "I'm alive! :)",
            )
        } else {
            call.respondText(
                text = "I'm dead x_x",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
    get(podReadinessPath) {
        val isReady = isReady(
            applicationState = applicationState,
            database = database,
        )
        if (isReady) {
            call.respondText(
                text = "I'm ready! :)",
            )
        } else {
            call.respondText(
                text = "Please wait! I'm not ready :(",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}

private fun isReady(
    applicationState: ApplicationState,
    database: DatabaseInterface,
): Boolean {
    return applicationState.ready && database.isOk()
}

private fun DatabaseInterface.isOk(): Boolean {
    return try {
        connection.use {
            it.isValid(1)
        }
    } catch (ex: Exception) {
        false
    }
}
