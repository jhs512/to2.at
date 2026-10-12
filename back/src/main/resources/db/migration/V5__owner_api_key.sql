CREATE SEQUENCE api_key_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE api_key (
    id BIGINT PRIMARY KEY DEFAULT nextval('api_key_id_seq'),
    owner_id BIGINT NOT NULL REFERENCES owner(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX api_key_owner_idx ON api_key(owner_id, id);
