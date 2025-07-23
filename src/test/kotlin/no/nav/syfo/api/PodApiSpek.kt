package no.nav.syfo.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
import no.nav.syfo.personoppgave.api.v2.podLivenessPath
import no.nav.syfo.personoppgave.api.v2.podReadinessPath
import no.nav.syfo.personoppgave.api.v2.registerPodApi
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.TestDatabaseNotResponding
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PodApiSpek : Spek({

    val database = TestDB()
    val databaseNotResponding = TestDatabaseNotResponding()

    fun ApplicationTestBuilder.setupPodApi(database: DatabaseInterface, applicationState: ApplicationState) {
        application {
            routing {
                registerPodApi(
                    applicationState = applicationState,
                    database = database,
                )
            }
        }
    }

    describe("Successful liveness and readiness checks") {
        it("Returns ok on is_alive") {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true)
                )

                val response = client.get(podLivenessPath)
                response.status.isSuccess() shouldBeEqualTo true
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }
        it("Returns ok on is_alive") {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true)
                )

                val response = client.get(podReadinessPath)
                response.status.isSuccess() shouldBeEqualTo true
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }
    }

    describe("Unsuccessful liveness and readiness checks") {
        it("Returns internal server error when liveness check fails") {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = false, ready = false)
                )

                val response = client.get(podLivenessPath)
                response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }

        it("Returns internal server error when readiness check fails") {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = false, ready = false)
                )

                val response = client.get(podReadinessPath)
                response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }
    }

    describe("Successful liveness and unsuccessful readiness checks when database not working") {
        it("Returns ok on is_alive") {
            testApplication {
                setupPodApi(
                    database = databaseNotResponding,
                    applicationState = ApplicationState(alive = true, ready = true)
                )

                val response = client.get(podLivenessPath)
                response.status.isSuccess() shouldBeEqualTo true
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }
        it("Returns internal server error when readiness check fails") {
            testApplication {
                setupPodApi(
                    database = databaseNotResponding,
                    applicationState = ApplicationState(alive = true, ready = true)
                )

                val response = client.get(podReadinessPath)
                response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }
    }
})
