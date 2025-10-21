UPDATE PERSON_OPPGAVE
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000'
WHERE uuid = '414d9410-e719-4bf3-8825-b5ece9ddbca5';
