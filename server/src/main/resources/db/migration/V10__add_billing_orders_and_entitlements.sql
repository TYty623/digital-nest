CREATE TABLE billing_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(64) NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    currency VARCHAR(3) NOT NULL,
    photo_limit INTEGER NOT NULL CHECK (photo_limit > 0),
    short_video_limit INTEGER NOT NULL CHECK (short_video_limit >= 0),
    timeline_limit INTEGER NOT NULL CHECK (timeline_limit > 0),
    theme_limit INTEGER NOT NULL CHECK (theme_limit > 0),
    hosted_years INTEGER NOT NULL CHECK (hosted_years > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(100) NOT NULL,
    payment_reference VARCHAR(100),
    paid_at TIMESTAMP WITH TIME ZONE,
    refunded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_billing_orders_status CHECK (status IN ('PENDING', 'PAID', 'CANCELED', 'REFUNDED')),
    CONSTRAINT uq_billing_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_billing_orders_payment_reference UNIQUE (payment_reference)
);

CREATE INDEX idx_billing_orders_user_created ON billing_orders(user_id, created_at DESC);

CREATE TABLE account_entitlements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id UUID NOT NULL UNIQUE REFERENCES billing_orders(id) ON DELETE CASCADE,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(64) NOT NULL,
    photo_limit INTEGER NOT NULL CHECK (photo_limit > 0),
    short_video_limit INTEGER NOT NULL CHECK (short_video_limit >= 0),
    timeline_limit INTEGER NOT NULL CHECK (timeline_limit > 0),
    theme_limit INTEGER NOT NULL CHECK (theme_limit > 0),
    hosted_until TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_account_entitlements_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_account_entitlements_active ON account_entitlements(user_id, status, hosted_until DESC);
