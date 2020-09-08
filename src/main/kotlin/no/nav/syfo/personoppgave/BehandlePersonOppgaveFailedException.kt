package no.nav.syfo.personoppgave

private const val messageStart = "Failed to proces PersonOppgave"

class BehandlePersonOppgaveFailedException(message: String = "") : RuntimeException("$messageStart: $message")
