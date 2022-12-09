CREATE TABLE motesvar (
    id                    SERIAL         PRIMARY KEY,
    uuid                  VARCHAR(50)    NOT NULL UNIQUE,
    mote_uuid             VARCHAR(50)    NOT NULL,
    arbeidstaker_ident    VARCHAR(11)    NOT NULL,
    svar_type             VARCHAR(30)    NOT NULL,
    sender_type           VARCHAR(15)    NOT NULL,
    brev_sent_at          TIMESTAMPTZ    NOT NULL,
    svar_received_at      TIMESTAMPTZ    NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_motesvar_mote_uuid ON motesvar(mote_uuid);
