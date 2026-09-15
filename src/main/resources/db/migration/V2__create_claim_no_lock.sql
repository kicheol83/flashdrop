CREATE TABLE claim_no_lock (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaign_no_lock(id),
    user_id VARCHAR(100) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_claim_no_lock_campaign_user UNIQUE (campaign_id, user_id)
);
