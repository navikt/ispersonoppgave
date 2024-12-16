UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE uuid = 'd5bfcd75-f587-41ae-b997-cbf9bda1bb94';
