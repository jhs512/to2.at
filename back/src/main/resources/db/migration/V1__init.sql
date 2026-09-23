-- Owner: a person who creates Links. Identified by their Kakao account.
-- The Kakao subject stays NOT NULL; development seed Owners use synthetic
-- values rather than nulls, so the "one Kakao account = one Owner" invariant
-- holds structurally (ADR 0005).
-- Sequences are declared with INCREMENT BY 1 and the entities use an allocation
-- size of 1 to match. A mismatch between the two is a startup failure, and the
-- ORM's default allocation size of 50 would not match this DDL.
CREATE SEQUENCE owner_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE owner
(
    id             BIGINT      NOT NULL DEFAULT nextval('owner_id_seq'),
    kakao_sub      TEXT        NOT NULL UNIQUE,
    nickname       TEXT        NOT NULL,
    email          TEXT,
    email_verified BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT owner_pk PRIMARY KEY (id)
);

-- Code allocation. Allocation size is 1 on the ORM side as well: the default of
-- 50 leaves gaps whenever the app restarts mid-block, and consecutive Codes are
-- the whole point of the scheme (ADR 0002).
CREATE SEQUENCE link_code_seq START WITH 1 INCREMENT BY 1;

-- Link: a Code, a Target and an Owner. The primary key IS the Code.
-- Deletion is a state change, never a row removal, so a Code is never reissued.
CREATE TABLE link
(
    id               BIGINT      NOT NULL DEFAULT nextval('link_code_seq'),
    target           TEXT        NOT NULL,
    owner_id         BIGINT      NOT NULL REFERENCES owner (id),
    state            TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    state_changed_at TIMESTAMPTZ,
    CONSTRAINT link_pk PRIMARY KEY (id),
    CONSTRAINT link_state_check CHECK (state IN ('ACTIVE', 'DELETED', 'SUSPENDED'))
);

CREATE INDEX link_owner_created_idx ON link (owner_id, created_at DESC);
