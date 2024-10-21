UPDATE person_oppgave
SET behandlet_tidspunkt = now(),
    behandlet_veileder_ident = 'X000000',
    publish = false,
    published_at = null
WHERE uuid IN (
'f39436ce-4ce5-4c8c-be09-921c35cd8345',
'9baff3e9-4183-4426-af87-facdb2de65de',
'c839a42d-5dd1-46b6-85f9-8d988f196c88',
'7fa22827-6616-4574-be6b-6ca8feb623a6');
