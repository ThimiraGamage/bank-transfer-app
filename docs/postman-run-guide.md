# Postman verification guide

Import the two JSON collections from `postman/`. Their collection variable `baseUrl` defaults to `http://localhost:8081` for Task 9 and `http://localhost:8082` for Task 12. Keep automatic redirects enabled so each POST assertion inspects the resulting rendered page. Requests use form-urlencoded fields, matching the Thymeleaf forms.

Each collection is a stateful sequence. Run it once, in order, against its own freshly seeded disposable database. Re-running the sequence on changed data will fail its balance assertions; changing the fee does not reset a database. Do not reuse the automated test schema while `mvn test` is running.

## Task 9 baseline

Start the baseline application with `bank.transfer.fee-rate=0` and a fresh database. Run the collection using Postman's Collection Runner. The requests cover:

1. Initial three customers, four account rows and LKR 85,000 total.
2. LKR 5,000 from Kamal to Nimali: balances 45,000 / 30,000 / 10,000; one successful transfer.
3. LKR 20,000 from Saman: insufficient balance; one failed transfer.
4. Same sender and receiver: rejected; second failed transfer.
5. LKR 1,000 simulated failure: debit rolled back; third failed transfer.
6. Empty and zero amount: field errors; transfer counts unchanged.
7. Nuwan, account 100004, opening balance 15,000: added; repeated account rejected; total100,000.
8. Search `sil`: the customer results contain only Kamal Silva.
9. Kamal's history: successful principal sent 5,000 and fees 0; successful and failed attempts retained.

The extra request numbers split the brief's combined cases into individually reviewable checks.

## Task 12 final extensions

Use a separate freshly seeded database and `bank.transfer.fee-rate=0.01`.

| Step | Kamal | Nimali | Saman | Bank fees | All-account total |
|---|---:|---:|---:|---:|---:|
| Initial | 50,000.00 | 25,000.00 | 10,000.00 | 0.00 | 85,000.00 |
| Send 5,000 Kamal→Nimali | 44,950.00 | 30,000.00 | 10,000.00 | 50.00 | 85,000.00 |
| Simulated failure 1,000 | 44,950.00 | 30,000.00 | 10,000.00 | 50.00 | 85,000.00 |
| Deposit 500 to Kamal | 45,450.00 | 30,000.00 | 10,000.00 | 50.00 | 85,500.00 |
| Withdraw 200 from Kamal | 45,250.00 | 30,000.00 | 10,000.00 | 50.00 | 85,300.00 |
| Send 10,001 Kamal→Saman | 35,148.99 | 30,000.00 | 20,001.00 | 150.01 | 85,300.00 |
| Send 10,000 Kamal→Nimali | 25,048.99 | 40,000.00 | 20,001.00 | 250.01 | 85,300.00 |

The large-transfer report with minimum 10,000 contains the 10,001 transfer and excludes the exact 10,000 transfer. Kamal's successful principal sent is 25,001 and total fees are 250.01. Cash operations and the failed attempt do not inflate either transfer aggregate.

## Optional command-line run

With Node.js and Newman available, the same collections can be executed from the project root:

```powershell
npx --yes newman run postman/Task09-Baseline.postman_collection.json
npx --yes newman run postman/Task12-Extensions.postman_collection.json
```

To preserve machine-readable runner evidence, add `--reporters cli,json --reporter-json-export <path.json>`. Describe a Newman run accurately as a Postman collection executed with Newman. Record the runner output, browser state, SQL results and Spring logs separately; an imported collection by itself does not establish that a test passed.
