CREATE SEQUENCE local_account_id_seq START WITH 1 INCREMENT BY 1;

-- Username/password accounts. The Operator is one of these and is NOT an Owner:
-- administration and ownership stay separate (ADR 0005). Development seed
-- accounts also live here and DO point at an Owner, which is how Owner flows can
-- be exercised locally without Kakao.
CREATE TABLE local_account
(
    id              BIGINT      NOT NULL DEFAULT nextval('local_account_id_seq'),
    username        TEXT        NOT NULL UNIQUE,
    -- Includes the algorithm prefix, so the hash can be migrated later without
    -- invalidating existing passwords.
    password_hash   TEXT        NOT NULL,
    operator        BOOLEAN     NOT NULL DEFAULT FALSE,
    owner_id        BIGINT REFERENCES owner (id),
    failed_attempts INT         NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT local_account_pk PRIMARY KEY (id)
);
