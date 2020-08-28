package no.nav.syfo.util

import no.nav.syfo.getEnvVar

fun isPreProd(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "dev-fss") == "dev-fss"
