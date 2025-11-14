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
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.TestDatabaseNotResponding
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PodApiTest {
    private val database = ExternalMockEnvironment.instance.database
    private val databaseNotResponding = TestDatabaseNotResponding()

    private fun ApplicationTestBuilder.setupPodApi(database: DatabaseInterface, applicationState: ApplicationState) {
        application {
            routing {
                registerPodApi(
                    applicationState = applicationState,
                    database = database,
                )
            }
        }
    }

    @Nested
    @DisplayName("Liveness and readiness checks")
    inner class SuccessfulLivenessAndReadiness {
        @Test
        fun `Returns ok on is_alive`() = testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = true, ready = true)
            )
            val response = client.get(podLivenessPath)
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `Returns ok on is_ready`() = testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = true, ready = true)
            )
            val response = client.get(podReadinessPath)
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }
    }

    @Nested
    @DisplayName("Unsuccessful liveness and readiness checks")
    inner class UnsuccessfulLivenessAndReadiness {
        @Test
        fun `Returns internal server error when liveness check fails`() = testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = false, ready = false)
            )
            val response = client.get(podLivenessPath)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `Returns internal server error when readiness check fails`() = testApplication {
            setupPodApi(
                database = database,
                applicationState = ApplicationState(alive = false, ready = false)
            )
            val response = client.get(podReadinessPath)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }

    @Nested
    @DisplayName("Database not working")
    inner class DatabaseNotWorking {
        @Test
        fun `Returns ok on is_alive when db not responding`() = testApplication {
            setupPodApi(
                database = databaseNotResponding,
                applicationState = ApplicationState(alive = true, ready = true)
            )
            val response = client.get(podLivenessPath)
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `Returns internal server error on is_ready when db not responding`() = testApplication {
            setupPodApi(
                database = databaseNotResponding,
                applicationState = ApplicationState(alive = true, ready = true)
            )
            val response = client.get(podReadinessPath)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }
}
