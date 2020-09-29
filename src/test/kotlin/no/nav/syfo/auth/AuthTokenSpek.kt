package no.nav.syfo.auth

import io.ktor.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@InternalAPI
object AuthTokenSpek : Spek({

    describe("getVeilederTokenPayload") {

        it("should parse and return a valid payload") {
            val tokenPayload = getVeilederTokenPayload(mockToken)

            tokenPayload.navIdent shouldBeEqualTo "Z991598"
            tokenPayload.epost shouldBeEqualTo "F_Z991598.E_Z991598@trygdeetaten.no"
            tokenPayload.navn shouldBeEqualTo "F_Z991598 E_Z991598"
        }
    }
})

val mockToken = """
    eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6InU0T2ZORlBId0VCb3NIanRyYXVPYlY4NExuWSIsImtpZCI6InU0T2ZORlBId0VCb3NIanRyYXVPYlY4NExuWSJ9.eyJhdWQiOiIzOGUwN2QzMS02NTlkLTQ1OTUtOTM5YS1mMThkY2UzNDQ2YzUiLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC85NjZhYzU3Mi1mNWI3LTRiYmUtYWE4OC1jNzY0MTljMGY4NTEvIiwiaWF0IjoxNTYzMTk0ODE2LCJuYmYiOjE1NjMxOTQ4MTYsImV4cCI6MTU2MzIzODMxNiwiYWlvIjoiNDJGZ1lKajEwOUd0TUdhNzQ3L20xL2JQUGQvVjF2dWJ2VjFrbDJWZXVQaXpZWFNnVVNNQSIsImFtciI6WyJwd2QiXSwiZmFtaWx5X25hbWUiOiJFX1o5OTE1OTgiLCJnaXZlbl9uYW1lIjoiRl9aOTkxNTk4IiwiZ3JvdXBzIjpbIjkyNGJhZGNkLWI5MzYtNDRmNC1iN2JmLTk3YzAzZGUwODkzYSIsIjkyODYzNmY0LWZkMGQtNDE0OS05NzhlLWE2ZmI2OGJiMTlkZSJdLCJpcGFkZHIiOiIxNTUuNTUuNjcuNTEiLCJuYW1lIjoiRl9aOTkxNTk4IEVfWjk5MTU5OCIsIm5vbmNlIjoid21WUGJjTWhsTzRXOU5WVW9vYWVwSVktN2NGT29SaVRucEJqTWhCMlYyayIsIm9pZCI6IjBlYzE3NGNlLTA2YjQtNDgzNy1iZjczLTY3MmQxZWUyNTNkOSIsIm9ucHJlbV9zaWQiOiJTLTEtNS0yMS0zMDMzODQ0OTEtMzA0NjQzMjg3MS0zMzQwOTgxNjc1LTE5NzQxOCIsInN1YiI6InVwcjRwVGtiT2hSUjJMUnZpcU1KQ0F1c3dUbkNfalZ1Vkl1R2s3dU5TTjgiLCJ0aWQiOiI5NjZhYzU3Mi1mNWI3LTRiYmUtYWE4OC1jNzY0MTljMGY4NTEiLCJ1bmlxdWVfbmFtZSI6IkZfWjk5MTU5OC5FX1o5OTE1OThAdHJ5Z2RlZXRhdGVuLm5vIiwidXBuIjoiRl9aOTkxNTk4LkVfWjk5MTU5OEB0cnlnZGVldGF0ZW4ubm8iLCJ1dGkiOiJyUXBKX1ZnQTQwNkxJSGtWdlJKUUFBIiwidmVyIjoiMS4wIiwiTkFWaWRlbnQiOiJaOTkxNTk4In0.Oeum8mgMQsUoU3YUqn9GB4bzNP1ZCkSXZvBwo3kByxijQUbQk5rk6TPJEmkxGnrCLIuR4VIC2kl9B3TDXRBXEdkaHmK8W3i5Y-Vorvhf6AoSGE1woP2BI1l-twgpvSx7-qHms2rLGRV7gbucegrvAPlNV2QaAYNhqH8z5jWWp7IYjqM5aXtGFpjNgl0LmgVk1Z_ro5WbEZ-zqIgaIGphx_JVVj1CMQAZbw4a_Pp7xaXZ8uhOU6dlwLU15VD89erxXiIoZTEEElZ3K-WdrfYJYClwv_2CrvpG2OwSEF2uE68ZqJ6xeuhul9pxFuKaZUG1Utk9gnVeYXpSs7CR6idOSw
""".trimIndent().trim()
