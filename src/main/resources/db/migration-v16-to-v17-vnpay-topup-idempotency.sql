-- MySQL 8: apply once after v16. Each VNPay transaction number may fund only one top-up.
-- Verify there are no duplicate non-null gateway_transaction_no values before applying.
CREATE UNIQUE INDEX uq_wallet_topups_gateway_txn_no
    ON wallet_topups (gateway_transaction_no);
