UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE referanse_uuid = '98848881-04a9-4a93-9053-2c825ac727dc';
