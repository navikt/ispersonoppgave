package no.nav.syfo.client.sts

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.util.basicHeader
import java.time.LocalDateTime

class StsRestClient(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    private val client = httpClientDefault()

    private var cachedOidcToken: Token? = null

    suspend fun token(): String {
        if (Token.shouldRenew(cachedOidcToken)) {
            val url = "$baseUrl/rest/v1/sts/token?grant_type=client_credentials&scope=openid"
            val response: HttpResponse = client.get(url) {
                header(HttpHeaders.Authorization, basicHeader(username, password))
                accept(ContentType.Application.Json)
            }

            cachedOidcToken = response.receive<Token>()
        }

        return cachedOidcToken!!.access_token
    }

    data class Token(
        val access_token: String,
        val token_type: String,
        val expires_in: Int,
    ) {
        val expirationTime: LocalDateTime = LocalDateTime.now().plusSeconds(expires_in - 10L)

        companion object {
            fun shouldRenew(token: Token?): Boolean {
                if (token == null) {
                    return true
                }

                return isExpired(token)
            }

            private fun isExpired(token: Token): Boolean {
                return token.expirationTime.isBefore(LocalDateTime.now())
            }
        }
    }
}
