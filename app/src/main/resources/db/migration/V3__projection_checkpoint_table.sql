CREATE TABLE projection_checkpoint (
    projection_name VARCHAR(255) PRIMARY KEY,
    last_processed_event_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION set_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_projection_checkpoint
BEFORE UPDATE ON projection_checkpoint
FOR EACH ROW
EXECUTE FUNCTION set_update_time();