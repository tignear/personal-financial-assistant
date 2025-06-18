CREATE TABLE event (
    id SERIAL PRIMARY KEY,
    stream_type VARCHAR(100) NOT NULL,
    stream_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    user_id BIGINT,
    CONSTRAINT event_stream_unique UNIQUE (stream_type, stream_id, version)
);
CREATE INDEX idx_event_user_id ON event(user_id);
CREATE INDEX idx_event_version ON event(version);
