CREATE TABLE claim_for_update (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaign_for_update(id),
    user_id VARCHAR(100) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_claim_for_update_campaign_user UNIQUE (campaign_id, user_id)
);
