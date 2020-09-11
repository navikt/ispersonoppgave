package no.nav.syfo.client.sts

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.*
import no.nav.syfo.util.responseJSON
import org.json.JSONObject
import java.time.LocalDateTime

class StsRestClient(val baseUrl: String, val username: String, val password: String) {
    private var cachedOidcToken: Token? = null

    fun token(): String {
        if (Token.shouldRenew(cachedOidcToken)) {
            val (_, _, result) = "$baseUrl/rest/v1/sts/token?grant_type=client_credentials&scope=openid"
                .httpGet()
                .authenticate(username, password)
                .header(mapOf(HttpHeaders.Accept to "application/json"))
                .responseJSON()

            cachedOidcToken = result.get().mapToToken()
        }

        return cachedOidcToken!!.accessToken
    }

    private fun JSONObject.mapToToken(): Token {
        return Token(
            getString("access_token"),
            getString("token_type"),
            getInt("expires_in")
        )
    }

    data class Token(val accessToken: String, val type: String, val expiresIn: Int) {
        val expirationTime: LocalDateTime = LocalDateTime.now().plusSeconds(expiresIn - 10L)

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
