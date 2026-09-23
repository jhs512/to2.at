CREATE SEQUENCE report_id_seq START WITH 1 INCREMENT BY 1;

-- A Report is someone telling us a Link is being abused. Anyone can file one;
-- no account is involved.
CREATE TABLE report
(
    id          BIGINT      NOT NULL DEFAULT nextval('report_id_seq'),
    link_id     BIGINT      NOT NULL REFERENCES link (id),
    reason      TEXT,
    reporter_ip TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    handled_at  TIMESTAMPTZ,
    CONSTRAINT report_pk PRIMARY KEY (id),
    -- One Report per source per Link. This is both the rate limit and what
    -- makes "reports from several distinct sources" a meaningful count: without
    -- it, one person could manufacture an automatic Suspension alone.
    CONSTRAINT report_unique_source UNIQUE (link_id, reporter_ip)
);

CREATE INDEX report_unhandled_idx ON report (created_at DESC) WHERE handled_at IS NULL;
