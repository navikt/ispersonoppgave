CREATE TABLE PERSON_OPPGAVE (
  id                            SERIAL             PRIMARY KEY,
  uuid                          VARCHAR(50)        NOT NULL UNIQUE,
  fnr                           VARCHAR(11)        NOT NULL,
  virksomhetsnummer             VARCHAR(9)         NOT NULL,
  type                          VARCHAR(30)        NOT NULL,
  oversikthendelse_tidspunkt    TIMESTAMP,
  behandlet_tidspunkt           TIMESTAMP,
  behandlet_veileder_ident      VARCHAR(7),
  opprettet                     TIMESTAMP          NOT NULL,
  sist_endret                   TIMESTAMP          NOT NULL
);
