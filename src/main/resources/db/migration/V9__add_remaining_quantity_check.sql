ALTER TABLE campaign_for_update
    ADD CONSTRAINT chk_campaign_for_update_remaining_non_negative CHECK (remaining_quantity >= 0);

ALTER TABLE campaign_optimistic
    ADD CONSTRAINT chk_campaign_optimistic_remaining_non_negative CHECK (remaining_quantity >= 0);

ALTER TABLE campaign_redis
    ADD CONSTRAINT chk_campaign_redis_remaining_non_negative CHECK (remaining_quantity >= 0);
