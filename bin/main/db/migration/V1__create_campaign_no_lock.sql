CREATE TABLE campaign_no_lock (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    total_quantity INTEGER NOT NULL,
    remaining_quantity INTEGER NOT NULL,
    starts_at TIMESTAMP NOT NULL
);
