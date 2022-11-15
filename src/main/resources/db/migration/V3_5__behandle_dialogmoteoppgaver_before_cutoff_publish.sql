UPDATE person_oppgave
SET behandlet_veileder_ident = 'X00000',
    behandlet_tidspunkt = now(),
    sist_endret = now(),
    publish = true
WHERE behandlet_veileder_ident IS null
  AND behandlet_tidspunkt IS null
  AND TYPE = 'DIALOGMOTESVAR';

