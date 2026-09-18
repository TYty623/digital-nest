CREATE TABLE media_deletion_queue (
    id UUID PRIMARY KEY,
    storage_filename VARCHAR(80) NOT NULL UNIQUE,
    purge_after TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_media_deletion_queue_purge_after ON media_deletion_queue(purge_after ASC);
