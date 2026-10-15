-- Double-entry ledger. Every journal entry's postings sum to zero; balances are the sum of
-- postings (debits positive, credits negative). Money is BIGINT paise, never floating point.

CREATE TABLE accounts (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(80) NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    type        VARCHAR(10) NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE')),
    owner_id    VARCHAR(60),
    balance     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_owner ON accounts (owner_id);

CREATE TABLE journal_entries (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    kind            VARCHAR(30) NOT NULL,
    description     VARCHAR(300) NOT NULL,
    reference       VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entries_reference ON journal_entries (reference);

-- Postings are append-only: corrections are new reversing entries, never UPDATEs.
CREATE TABLE postings (
    id         BIGSERIAL PRIMARY KEY,
    entry_id   BIGINT NOT NULL REFERENCES journal_entries (id),
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    amount     BIGINT NOT NULL CHECK (amount <> 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_postings_account ON postings (account_id, id DESC);
CREATE INDEX idx_postings_entry ON postings (entry_id);

CREATE TABLE payouts (
    id              VARCHAR(40) PRIMARY KEY,
    idempotency_key VARCHAR(120) UNIQUE,
    worker_id       VARCHAR(60) NOT NULL,
    kind            VARCHAR(10) NOT NULL,
    amount          BIGINT NOT NULL CHECK (amount > 0),
    fee             BIGINT NOT NULL CHECK (fee >= 0),
    upi_id          VARCHAR(100) NOT NULL,
    status          VARCHAR(12) NOT NULL,
    provider_ref    VARCHAR(60),
    attempts        INT NOT NULL DEFAULT 0,
    failure_reason  VARCHAR(200),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payouts_worker ON payouts (worker_id, created_at DESC);
CREATE INDEX idx_payouts_work ON payouts (status, next_attempt_at);

-- The mock bank / payout provider keeps its own books, so reconciliation compares two independent records.
CREATE TABLE provider_transfers (
    reference    VARCHAR(40) PRIMARY KEY,
    provider_ref VARCHAR(60) NOT NULL UNIQUE,
    amount       BIGINT NOT NULL,
    upi_id       VARCHAR(100) NOT NULL,
    status       VARCHAR(12) NOT NULL,
    reason       VARCHAR(200),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    settles_at   TIMESTAMPTZ NOT NULL,
    callback_sent BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE processed_events (
    event_id     VARCHAR(60) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(100) NOT NULL,
    payload      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

-- System accounts.
INSERT INTO accounts (code, name, type) VALUES
    ('bank:float', 'GigPay bank account (payout float)', 'ASSET'),
    ('clearing:payouts-in-transit', 'Payouts sent to the bank, not yet confirmed', 'LIABILITY'),
    ('income:payout-fees', 'Instant payout fees', 'INCOME'),
    ('equity:capital', 'Founders'' capital', 'EQUITY');
