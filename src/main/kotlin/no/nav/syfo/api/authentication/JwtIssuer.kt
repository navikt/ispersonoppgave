package no.nav.syfo.api.authentication

data class JwtIssuer(
    val acceptedAudienceList: List<String>,
    val jwtIssuerType: JwtIssuerType,
    val wellKnown: WellKnown
)

enum class JwtIssuerType {
    INTERN_AZUREAD_V1,
    INTERN_AZUREAD_V2,
}
