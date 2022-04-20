package no.nav.syfo.client.azuread.v2

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.syfo.client.httpClientProxy
import org.slf4j.LoggerFactory

class AzureAdV2Client(
    private val azureAppClientId: String,
    private val azureAppClientSecret: String,
    private val azureTokenEndpoint: String,
) {
    private val httpClient = httpClientProxy()

    private val mutex = Mutex()

    @Volatile
    private var tokenMap = HashMap<String, AzureAdV2Token>()

    suspend fun getOnBehalfOfToken(
        scopeClientId: String,
        token: String,
    ): AzureAdV2Token? {
        return getAccessToken(
            Parameters.build {
                append("client_id", azureAppClientId)
                append("client_secret", azureAppClientSecret)
                append("client_assertion_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", token)
                append("scope", "api://$scopeClientId/.default")
                append("requested_token_use", "on_behalf_of")
            }
        )?.toAzureAdV2Token()
    }

    suspend fun getSystemToken(scopeClientId: String): AzureAdV2Token? {
        return mutex.withLock {
            (
                tokenMap[scopeClientId]
                    ?.takeUnless { cachedToken ->
                        cachedToken.isExpired()
                    }
                    ?: run {
                        getAccessToken(
                            Parameters.build {
                                append("client_id", azureAppClientId)
                                append("client_secret", azureAppClientSecret)
                                append("grant_type", "client_credentials")
                                append("scope", "api://$scopeClientId/.default")
                            }
                        )?.let {
                            val azureadToken = it.toAzureAdV2Token()
                            tokenMap[scopeClientId] = azureadToken
                            azureadToken
                        }
                    }
                )
        }
    }

    private suspend fun getAccessToken(
        formParameters: Parameters,
    ): AzureAdV2TokenResponse? {
        return try {
            val response: HttpResponse = httpClient.post(azureTokenEndpoint) {
                accept(ContentType.Application.Json)
                setBody(FormDataContent(formParameters))
            }
            response.body<AzureAdV2TokenResponse>()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e)
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e)
        }
    }

    private fun handleUnexpectedResponseException(
        responseException: ResponseException,
    ): AzureAdV2TokenResponse? {
        log.error(
            "Error while requesting AzureAdAccessToken with statusCode=${responseException.response.status.value}",
            responseException
        )
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(AzureAdV2Client::class.java)
    }
}
