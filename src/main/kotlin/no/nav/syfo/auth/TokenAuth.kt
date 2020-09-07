package no.nav.syfo.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.request.RequestCookies
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.api.getWellKnown
import no.nav.syfo.getEnvironment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.auth")

const val expectedCookieName = "isso-idtoken"

fun getTokenFromCookie(cookies: RequestCookies): String {
    return cookies[expectedCookieName].toString()
}

fun getDecodedTokenFromCookie(cookies: RequestCookies): DecodedJWT? {
    val token = cookies[expectedCookieName]

    return if (token != null) {
        verifyToken(token, getEnvironment())
    } else {
        null
    }
}

data class VeilederTokenPayload(
    val navIdent: String,
    val navn: String,
    val epost: String
)

fun getVeilederTokenPayload(token: String): VeilederTokenPayload {
    val decodedJWT = JWT.decode(token)
    val navIdent: String = decodedJWT.claims["NAVident"]?.asString()
        ?: throw Error("Missing NAVident in private claims")
    val navn: String = decodedJWT.claims["name"]?.asString() ?: throw Error("Missing name in private claims")
    val email = decodedJWT.claims["unique_name"]?.asString() ?: throw Error("Missing unique_name in private claims")
    return VeilederTokenPayload(navIdent, navn, email)
}

fun isInvalidToken(cookies: RequestCookies): Boolean {
    val decodedToken = getDecodedTokenFromCookie(cookies)
    val env = getEnvironment()

    if (decodedToken != null) {
        return if (!decodedToken.audience.contains(env.loginserviceClientId)) {
            log.warn(
                "Auth: Unexpected audience for jwt {}, {}, {}",
                StructuredArguments.keyValue("issuer", decodedToken.issuer),
                StructuredArguments.keyValue("audience", decodedToken.audience),
                StructuredArguments.keyValue("expectedAudience", env.loginserviceClientId)
            )
            true
        } else {
            false
        }
    } else {
        log.warn("Token not verified: No token")
        return true
    }
}

fun verifyToken(token: String, env: Environment): DecodedJWT {
    val wellKnown = getWellKnown(env.aadDiscoveryUrl)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val jwt = JWT.decode(token)
    val jwk = jwkProvider.get(jwt.keyId)

    val publicKey = jwk.publicKey as? RSAPublicKey ?: throw Exception("Invalid key type")

    val algorithm = when (jwk.algorithm) {
        "RS256" -> Algorithm.RSA256(publicKey, null)
        "RS384" -> Algorithm.RSA384(publicKey, null)
        "RS512" -> Algorithm.RSA512(publicKey, null)
        "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
        "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
        "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
        null -> Algorithm.RSA256(publicKey, null)
        else -> throw IllegalArgumentException("Unsupported algorithm $jwk.algorithm")
    }

    val verifier = JWT.require(algorithm) // signature
        .withIssuer(env.jwtIssuer) // iss
        .withAudience(env.loginserviceClientId) // aud
        .build()

    return verifier.verify(token)
}
