package no.nav.syfo

import io.ktor.http.*
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.api.registerPodApi
import no.nav.syfo.api.registerPrometheusApi
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket

object SelftestSpek : Spek({
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val applicationState = ApplicationState()

    describe("Calling selftest with successful liveness and readyness tests") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(applicationState)
                registerPrometheusApi()
            }

            it("Returns ok on is_alive") {
                applicationState.running = true

                with(handleRequest(HttpMethod.Get, "/is_alive")) {
                    response.status()?.isSuccess() shouldEqual true
                    response.content shouldNotEqual null
                }
            }
            it("Returns ok on is_alive") {
                applicationState.initialized = true

                with(handleRequest(HttpMethod.Get, "/is_ready")) {
                    println(response.status())
                    response.status()?.isSuccess() shouldEqual true
                    response.content shouldNotEqual null
                }
            }
            it("Returns error on failed is_alive") {
                applicationState.running = false

                with(handleRequest(HttpMethod.Get, "/is_alive")) {
                    response.status()?.isSuccess() shouldNotEqual true
                    response.content shouldNotEqual null
                }
            }
            it("Returns error on failed is_ready") {
                applicationState.initialized = false

                with(handleRequest(HttpMethod.Get, "/is_ready")) {
                    response.status()?.isSuccess() shouldNotEqual true
                    response.content shouldNotEqual null
                }
            }
        }
    }

    describe("Calling selftests with unsucessful liveness test") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(ApplicationState(running = false))
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, "/is_alive")) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                    response.content shouldNotEqual null
                }
            }
        }
    }

    describe("Calling selftests with unsucessful readyness test") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(ApplicationState(initialized = false))
            }

            it("Returns internal server error when readyness check fails") {
                with(handleRequest(HttpMethod.Get, "/is_ready")) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                    response.content shouldNotEqual null
                }
            }
        }
    }
})
