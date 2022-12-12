CREATE TABLE dialogmote_statusendring (
    id                    SERIAL         PRIMARY KEY,
    uuid                  VARCHAR(50)    NOT NULL UNIQUE,
    mote_uuid             VARCHAR(50)    NOT NULL,
    arbeidstaker_ident    VARCHAR(11)    NOT NULL,
    veileder_ident        VARCHAR(7)     NOT NULL,
    type                  VARCHAR(30)    NOT NULL,
    endring_tidspunkt     TIMESTAMPTZ    NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_dialogmote_statusendring_mote_uuid ON dialogmote_statusendring(mote_uuid);
