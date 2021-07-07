package no.nav.syfo.auth

import com.auth0.jwt.JWT
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.auth")

const val JWT_CLAIM_NAVIDENT = "NAVident"

fun getNAVIdentFromToken(token: String): String {
    val decodedJWT = JWT.decode(token)
    return decodedJWT.claims[JWT_CLAIM_NAVIDENT]?.asString()
        ?: throw Error("Missing NAVident in private claims")
}
