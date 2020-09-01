package no.nav.syfo.testutil.generator

import no.nav.syfo.client.enhet.BehandlendeEnhet
import no.nav.syfo.testutil.UserConstants.NAV_ENHET

val generateBehandlendeEnhet =
        BehandlendeEnhet(
                enhetId = "1234",
                navn = NAV_ENHET
        )
