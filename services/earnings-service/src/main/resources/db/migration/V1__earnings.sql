CREATE TABLE platforms (
    id                 VARCHAR(20) PRIMARY KEY,
    name               VARCHAR(60) NOT NULL,
    kind               VARCHAR(12) NOT NULL,               -- DELIVERY or RIDE
    webhook_secret     VARCHAR(80) NOT NULL,
    commission_bps     INT NOT NULL,                       -- platform commission, basis points of the fare
    base_paise         BIGINT NOT NULL,
    per_km_paise       BIGINT NOT NULL,
    per_min_paise      BIGINT NOT NULL,                    -- ride time (rides) or wait time beyond 5 min (deliveries)
    streak_rules       TEXT NOT NULL                       -- JSON: [{"trips":10,"bonusPaise":10000}, ...]
);

CREATE TABLE workers (
    id         VARCHAR(20) PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    phone      VARCHAR(10) NOT NULL UNIQUE,
    pin_hash   VARCHAR(100),
    city       VARCHAR(40) NOT NULL,
    vehicle    VARCHAR(20) NOT NULL,
    upi_id     VARCHAR(100) NOT NULL,
    platforms  VARCHAR(100) NOT NULL,                      -- comma-separated platform ids
    language   VARCHAR(5) NOT NULL DEFAULT 'en',
    joined_on  DATE NOT NULL
);

CREATE TABLE trips (
    id               VARCHAR(80) PRIMARY KEY,               -- platform:externalId, the idempotency key
    platform_id      VARCHAR(20) NOT NULL REFERENCES platforms (id),
    worker_id        VARCHAR(20) NOT NULL REFERENCES workers (id),
    kind             VARCHAR(12) NOT NULL,
    distance_km      NUMERIC(6, 2) NOT NULL,
    duration_min     INT NOT NULL,
    wait_min         INT NOT NULL,
    surge            NUMERIC(4, 2) NOT NULL,
    fare_paise       BIGINT NOT NULL,
    incentive_paise  BIGINT NOT NULL,
    commission_paise BIGINT NOT NULL,
    tip_paise        BIGINT NOT NULL,
    net_paise        BIGINT NOT NULL,
    breakdown        TEXT NOT NULL,
    pickup_area      VARCHAR(60),
    drop_area        VARCHAR(60),
    completed_at     TIMESTAMPTZ NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trips_worker ON trips (worker_id, completed_at DESC);
CREATE INDEX idx_trips_platform_time ON trips (platform_id, completed_at DESC);

CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(100) NOT NULL,
    payload      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

INSERT INTO platforms VALUES
    ('FOODRUNNER', 'FoodRunner', 'DELIVERY', 'whsec_foodrunner_dev', 0, 2500, 600, 100,
     '[{"trips":10,"bonusPaise":10000},{"trips":15,"bonusPaise":15000}]'),
    ('RIDENOW', 'RideNow', 'RIDE', 'whsec_ridenow_dev', 1800, 2000, 900, 100,
     '[{"trips":12,"bonusPaise":15000}]'),
    ('QUICKDROP', 'QuickDrop', 'DELIVERY', 'whsec_quickdrop_dev', 0, 3000, 700, 0,
     '[{"trips":8,"bonusPaise":8000},{"trips":14,"bonusPaise":16000}]');
