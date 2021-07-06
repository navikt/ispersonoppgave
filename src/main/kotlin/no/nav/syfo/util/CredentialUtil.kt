package no.nav.syfo.util

import java.util.*

fun basicHeader(
    credentialUsername: String,
    credentialPassword: String,
) = "Basic " + Base64.getEncoder().encodeToString(java.lang.String.format("%s:%s", credentialUsername, credentialPassword).toByteArray())

fun bearerHeader(token: String): String {
    return "Bearer $token"
}
