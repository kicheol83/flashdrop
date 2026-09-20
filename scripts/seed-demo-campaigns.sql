DELETE FROM claim_no_lock
WHERE campaign_id = (SELECT id FROM campaign_no_lock WHERE code = 'FLASH50');

DELETE FROM claim_for_update
WHERE campaign_id = (SELECT id FROM campaign_for_update WHERE code = 'FLASH50');

DELETE FROM claim_optimistic
WHERE campaign_id = (SELECT id FROM campaign_optimistic WHERE code = 'FLASH50');

INSERT INTO campaign_no_lock (code, total_quantity, remaining_quantity, starts_at)
VALUES ('FLASH50', 50, 50, NOW() - INTERVAL '1 minute')
ON CONFLICT (code) DO UPDATE SET remaining_quantity = EXCLUDED.remaining_quantity, starts_at = EXCLUDED.starts_at;

INSERT INTO campaign_for_update (code, total_quantity, remaining_quantity, starts_at)
VALUES ('FLASH50', 50, 50, NOW() - INTERVAL '1 minute')
ON CONFLICT (code) DO UPDATE SET remaining_quantity = EXCLUDED.remaining_quantity, starts_at = EXCLUDED.starts_at;

INSERT INTO campaign_optimistic (code, total_quantity, remaining_quantity, starts_at, version)
VALUES ('FLASH50', 50, 50, NOW() - INTERVAL '1 minute', 0)
ON CONFLICT (code) DO UPDATE SET remaining_quantity = EXCLUDED.remaining_quantity, starts_at = EXCLUDED.starts_at;
