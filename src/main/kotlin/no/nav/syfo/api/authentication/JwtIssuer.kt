package no.nav.syfo.api.authentication

data class JwtIssuer(
    val accectedAudienceList: List<String>,
    val jwtIssuerType: JwtIssuerType,
    val wellKnown: WellKnown
)

enum class JwtIssuerType {
    INTERN_AZUREAD_V1,
}
