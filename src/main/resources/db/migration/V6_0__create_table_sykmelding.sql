CREATE TABLE sykmelding_personoppgave (
    id                    SERIAL         PRIMARY KEY,
    created_at            TIMESTAMPTZ    NOT NULL,
    personoppgave_id      INTEGER NOT NULL REFERENCES PERSON_OPPGAVE (id) ON DELETE CASCADE,
    tiltak_nav            TEXT           NOT NULL,
    tiltak_andre          TEXT           NOT NULL,
    bistand               TEXT           NOT NULL,
    duplicate_count       INT            NOT NULL
);

CREATE INDEX ix_sykmelding_personoppgave_id ON sykmelding_personoppgave(personoppgave_id);
