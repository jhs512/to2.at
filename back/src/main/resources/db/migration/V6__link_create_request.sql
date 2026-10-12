-- Optional request identity for replay-safe external create-and-copy flows.
-- NULL retains ordinary creation: equal Targets can still be separate Links.
ALTER TABLE link ADD COLUMN create_request_id UUID;
CREATE UNIQUE INDEX link_owner_create_request_idx ON link (owner_id, create_request_id);
