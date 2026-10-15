-- Each attempt at the bank gets its own reference (payout id + "." + attempt). A network retry of
-- the same attempt reuses the reference, so the bank de-duplicates it and can never pay twice; a
-- new reference is used only after the bank has definitively failed the previous attempt.
ALTER TABLE payouts ADD COLUMN bank_attempt INT NOT NULL DEFAULT 1;
