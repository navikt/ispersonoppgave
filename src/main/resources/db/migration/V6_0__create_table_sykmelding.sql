CREATE TABLE sykmelding (
    id                    SERIAL         PRIMARY KEY,
    created_at            TIMESTAMPTZ    NOT NULL,
    referanse_uuid        CHAR(36)       NOT NULL,
    personident           VARCHAR(11)    NOT NULL,
    tiltak_nav            TEXT           NOT NULL,
    tiltak_andre          TEXT           NOT NULL,
    bistand               TEXT           NOT NULL,
    duplicate_count       INT            NOT NULL
);

CREATE INDEX ix_sykmelding_personident ON sykmelding(personident);
