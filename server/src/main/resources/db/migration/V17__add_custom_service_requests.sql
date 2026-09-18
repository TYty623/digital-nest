CREATE TABLE custom_service_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_details VARCHAR(280) NOT NULL,
    request_details VARCHAR(3000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    materials_ready BOOLEAN NOT NULL DEFAULT FALSE,
    assignee VARCHAR(80),
    due_date DATE,
    revision_count INTEGER NOT NULL DEFAULT 0 CHECK (revision_count >= 0 AND revision_count <= 50),
    customer_message VARCHAR(800),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_custom_service_request_status CHECK (
        status IN ('SUBMITTED', 'MATERIALS_PENDING', 'IN_PROGRESS', 'REVISION', 'OUT_OF_SCOPE', 'DELIVERED', 'COMPLETED', 'CANCELED')
    )
);

CREATE INDEX idx_custom_service_requests_user_created ON custom_service_requests(user_id, created_at DESC);
CREATE INDEX idx_custom_service_requests_status_updated ON custom_service_requests(status, updated_at DESC);
