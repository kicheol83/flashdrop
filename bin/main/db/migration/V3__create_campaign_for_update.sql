CREATE TABLE campaign_for_update (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    total_quantity INTEGER NOT NULL,
    remaining_quantity INTEGER NOT NULL,
    starts_at TIMESTAMP NOT NULL
);
