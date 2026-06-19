package no.nav.syfo.testutil.mock

import no.nav.syfo.api.authentication.WellKnown
import java.nio.file.Paths

fun wellKnownInternADV2Mock(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        authorization_endpoint = "authorizationendpoint",
        token_endpoint = "tokenendpoint",
        jwks_uri = uri.toString(),
        issuer = "https://sts.issuer.net/v2"
    )
}
