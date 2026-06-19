package no.nav.syfo.testutil.mock

import no.nav.syfo.common.mock.tilgangskontroll.MockUserSyfoTilgangLevel
import no.nav.syfo.common.mock.tilgangskontroll.MockUserTilgangDetails
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.testutil.UserConstants

val mockTilgangDetailsPerNavident =
    mapOf(
        Navident(UserConstants.VEILEDER_IDENT) to MockUserTilgangDetails(
            syfoTilgangLevel = MockUserSyfoTilgangLevel.FULL,
            personsUserHasAccessTo = setOf(
                Personident(UserConstants.ARBEIDSTAKER_FNR.value),
            )
        ),
        Navident(UserConstants.VEILEDER_IDENT_READ_ACCESS) to MockUserTilgangDetails(
            syfoTilgangLevel = MockUserSyfoTilgangLevel.READ,
            personsUserHasAccessTo = setOf(
                Personident(UserConstants.ARBEIDSTAKER_FNR.value),
            )
        )
    )
