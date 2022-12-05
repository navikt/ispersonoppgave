UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE referanse_uuid IN ('960e67a5-4d81-4dee-a055-39a9cee2aa86');
