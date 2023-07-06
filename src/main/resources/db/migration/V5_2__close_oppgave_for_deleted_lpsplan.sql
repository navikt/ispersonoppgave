UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000'
WHERE referanse_uuid = '6da178c7-db38-462b-8e61-82ce1a680e2b';
