UPDATE PERSON_OPPGAVE
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE uuid = 'a0a40e06-e15b-4255-869f-9cece1b70d65';
