UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE referanse_uuid IN ('82755908-679a-434e-8f41-a6ed3ac0140d');
