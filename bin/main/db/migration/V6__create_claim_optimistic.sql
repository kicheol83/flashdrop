CREATE TABLE claim_optimistic (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaign_optimistic(id),
    user_id VARCHAR(100) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_claim_optimistic_campaign_user UNIQUE (campaign_id, user_id)
);
