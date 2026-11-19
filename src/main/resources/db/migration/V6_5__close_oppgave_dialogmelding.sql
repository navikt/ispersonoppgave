UPDATE PERSON_OPPGAVE
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE uuid = '6329fbdb-47f4-4967-ae1a-2ded6dc6a129';
