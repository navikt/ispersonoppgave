UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE referanse_uuid IN ('8b02bd2f-0bc4-401b-b46b-94f2adda78f3');
