-- Bank Transfer System: read-only evidence queries.
-- Select the intended database in your client before running these statements.
-- Normal local app: bank_db on port 3306.
-- Isolated evidence: bank_assignment_baseline / bank_assignment_final on port 3307.
-- Automated tests: bank_assignment_tests on port 3307 (tables are recreated).

SELECT DATABASE() AS selected_database, VERSION() AS server_version;

SELECT table_name, engine
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('customers', 'bank_transactions')
ORDER BY table_name;

SELECT id, account_number, full_name, email, balance
FROM customers
ORDER BY account_number;

SELECT SUM(balance) AS total_including_bank_fees
FROM customers;

SELECT COUNT(*) AS all_account_rows,
       SUM(CASE WHEN account_number <> '999999' THEN 1 ELSE 0 END) AS customer_accounts
FROM customers;

SELECT id, type, from_account, to_account, amount, fee,
       status, description, created_at
FROM bank_transactions
ORDER BY created_at DESC, id DESC;

SELECT type, status, COUNT(*) AS operation_count,
       SUM(amount) AS principal_total, SUM(fee) AS fee_total
FROM bank_transactions
GROUP BY type, status
ORDER BY type, status;

-- Transfer statistics exclude cash deposits/withdrawals.
SELECT status, COUNT(*) AS transfer_count
FROM bank_transactions
WHERE type = 'TRANSFER'
GROUP BY status;

-- Kamal's successful transfer principal and fees are separate totals.
SELECT COALESCE(SUM(amount), 0.00) AS total_principal_sent,
       COALESCE(SUM(fee), 0.00) AS total_fees_charged
FROM bank_transactions
WHERE type = 'TRANSFER' AND status = 'SUCCESS'
  AND from_account = '100001';

-- Strict threshold: an amount exactly 10000.00 is excluded.
SELECT id, from_account, to_account, amount, fee, status
FROM bank_transactions
WHERE type = 'TRANSFER' AND status = 'SUCCESS' AND amount > 10000.00
ORDER BY created_at DESC, id DESC;

-- Kamal's complete history, including failed attempts and cash operations.
SELECT id, type, from_account, to_account, amount, fee, status, description
FROM bank_transactions
WHERE from_account = '100001' OR to_account = '100001'
ORDER BY created_at DESC, id DESC;

-- Task 10 repair is deliberately NOT executed by this script.
-- In a disposable ZERO-FEE experiment only, after proving exactly 1000.00 was
-- lost from account 100001, the brief's repair is:
-- UPDATE customers SET balance = balance + 1000.00
-- WHERE account_number = '100001';
-- Do not repeat it: a second repair would create money.
