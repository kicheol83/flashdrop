CREATE TABLE claim_redis (
    id UUID PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaign_redis(id),
    user_id VARCHAR(100) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_claim_redis_campaign_user UNIQUE (campaign_id, user_id)
);
