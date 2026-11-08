# Bank Transfer System — Assignment 01

Spring Boot 4.0.9, Java 21, Thymeleaf, Spring Data JPA and MySQL.

**Student:** Nimsara · EC/2022/052  
**Course:** BECS 32263 — Full-Stack Software Development, University of Kelaniya  
**Repository:** https://github.com/ThimiraGamage/bank-transfer-app

The application demonstrates atomic transfers, deliberate rollback after debit, independent failure auditing with `REQUIRES_NEW`, custom JPQL/native/update queries, customer search and history. Extensions add a large-transfer report, cash deposits and withdrawals, and a 1% fee credited to internal account `999999`.

## Run locally

Install JDK 21 or later and Maven, enable Lombok support in the IDE, and start MySQL or XAMPP's MySQL-compatible server on port **3306**. The default datasource creates `bank_db` and uses `root` with an empty password.

If credentials differ, copy `secrets.properties.example` to `secrets.properties` and edit the values. The application imports this file; Git ignores it.

```powershell
Copy-Item secrets.properties.example secrets.properties
mvn spring-boot:run
```

Open http://localhost:8080. The first start seeds an empty database:

| Account | Name | Opening balance (LKR) |
|---|---|---:|
| 100001 | Kamal Silva | 50,000.00 |
| 100002 | Nimali Perera | 25,000.00 |
| 100003 | Saman Fernando | 10,000.00 |
| 999999 | Bank Fees (internal) | 0.00 |

There are three customers and four account rows. Total balance includes the bank fee account, so an internal transfer preserves LKR 85,000.00. Creating an account with an opening balance or making a cash deposit intentionally adds money to the model; cash withdrawal removes it.

This checkout does not contain a Maven wrapper: use `mvn`, not `mvnw`.

## Baseline and extension runs

The final configuration charges **1%**. A LKR 5,000 transfer debits LKR 5,050 from the sender, credits LKR 5,000 to the receiver and credits LKR 50 to `999999`. Fees use `BigDecimal` and `HALF_UP` rounding to two decimal places. Sender affordability includes the fee.

Task 9 in the assignment precedes the fee extension. Run with a zero fee to reproduce its exact balances:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--bank.transfer.fee-rate=0"
```

Changing the fee does **not** reset existing balances. Use separate fresh disposable databases for the baseline and extension collections. Example commands for the assignment evidence server on port 3307:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081 --bank.transfer.fee-rate=0 --spring.datasource.url=jdbc:mysql://127.0.0.1:3307/bank_assignment_baseline?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
```

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8082 --bank.transfer.fee-rate=0.01 --spring.datasource.url=jdbc:mysql://127.0.0.1:3307/bank_assignment_final?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
```

Both commands expect the server to be available and use the configured datasource credentials. Run each collection once, in order, against fresh seed data; do not point an evidence run at an account database that must be retained.

## Build and test

The integration tests use an isolated MySQL-compatible server at **127.0.0.1:3307**, user `root`, empty password and database **bank_assignment_tests**. They deliberately recreate tables with `create-drop` and reseed data between tests. They must not use the normal `bank_db` database. Ensure that isolated server is running before executing:

```powershell
mvn test
mvn package
```

The tests cover committed transfers and fees, rollback after an executed debit, independent failure audit, validation, queries, cash operations, concurrency, and the controlled unprotected `SUPPORTS` experiment. Surefire reports are written under `target/surefire-reports/`; supporting evidence is written under `assignment-work/evidence/`. No web server is started by these service tests.

After packaging, run the application with:

```powershell
java -jar target/bank-transfer-app-0.0.1-SNAPSHOT.jar
```

## Reproducible requests and evidence

- [Postman run guide](docs/postman-run-guide.md): collection order, ports and expected balances.
- [Baseline collection](postman/Task09-Baseline.postman_collection.json): all seven Task 9 cases, with separate validation requests.
- [Extension collection](postman/Task12-Extensions.postman_collection.json): fee, rollback, deposit, withdrawal, threshold and history checks.
- [Database checks](docs/database-checks.sql): read-only SQL for balances, records, aggregates and InnoDB verification.
- [GitHub workflow](docs/github-workflow.md): feature branch, pull request, review and merge commands.

The collections submit the application's form fields as `application/x-www-form-urlencoded`, follow redirects and test the rendered HTML. Failed business operations retain a `FAILED` audit; invalid form fields are rejected before a banking transaction starts. The latest table includes transfers and cash operations, while transfer statistics and transfer aggregates filter by operation type. History reports principal sent and fees separately.

## Important implementation choices

The public `BankService.transfer` method uses `@Transactional(rollbackFor = Exception.class)`. Conditional debit, receiver credit, bank credit and one success record share that transaction. The controller catches failures only after rollback and calls a separate audit bean with `REQUIRES_NEW`. The debit query checks `balance >= :amount` in the same update that subtracts it, avoiding a separate read-and-spend race.

`/large-transfers?min=10000` returns successful customer transfers **strictly greater** than the threshold. Deposit and withdrawal records do not count as customer transfers. Bank account `999999` cannot be selected for ordinary customer operations.

This is a classroom demonstration without authentication, an idempotency key or a production accounting ledger. Keep the unsafe transaction experiment confined to disposable test data; the application retains the protected transfer annotation.
