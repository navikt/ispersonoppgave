UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = true,
    published_at = null
WHERE referanse_uuid IN ('012ed72f-3ebd-4f43-9db6-844b6de065d8',
                         '242cc4fe-a855-4b68-8272-f0791fcd9dd5',
                         '31bd2780-2a81-4575-ba8b-ba9f5949a13a',
                         '501cfcce-e348-4600-a329-80f37b6dbef7');
