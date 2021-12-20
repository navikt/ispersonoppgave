package no.nav.syfo.api

import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.TestDatabaseNotResponding
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PodApiSpek : Spek({

    describe("Successful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            val database = TestDB()
            application.routing {
                registerPodApi(
                    applicationState = ApplicationState(
                        alive = true,
                        ready = true,
                    ),
                    database = database,
                )
            }

            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, podLivenessPath)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, podReadinessPath)) {
                    println(response.status())
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }

    describe("Unsuccessful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            val database = TestDB()
            application.routing {
                registerPodApi(
                    applicationState = ApplicationState(
                        alive = false,
                        ready = false,
                    ),
                    database = database,
                )
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, podLivenessPath)) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, podReadinessPath)) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
    describe("Successful liveness and unsuccessful readiness checks when database not working") {
        with(TestApplicationEngine()) {
            start()
            val database = TestDatabaseNotResponding()
            application.routing {
                registerPodApi(
                    applicationState = ApplicationState(
                        alive = true,
                        ready = true,
                    ),
                    database = database,
                )
            }

            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, podLivenessPath)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, podReadinessPath)) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
})
