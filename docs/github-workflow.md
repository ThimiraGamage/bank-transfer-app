# GitHub feature branch and pull request

Repository: https://github.com/ThimiraGamage/bank-transfer-app

These commands describe the required workflow. The report should show the actual branch, pull request and merge evidence, rather than treating this guide as proof that the steps occurred.

For a new local repository, initialise the starter on `main`, check the ignored files and make the initial commit:

```powershell
git init -b main
git status --short
git add .gitignore pom.xml README.md src
git commit -m "Create bank transfer project"
git remote add origin https://github.com/ThimiraGamage/bank-transfer-app.git
git push -u origin main
git switch -c feature/bank-transfer
```

If Git is already initialised or `origin` exists, inspect `git status`, `git branch` and `git remote -v` first; retain the existing history and remote instead of repeating initialisation.

After the implementation and checks, stage the intended files and inspect the result:

```powershell
mvn test
git add src pom.xml README.md .gitignore docs postman
git diff --cached --stat
git diff --cached --name-only
git commit -m "Add atomic transfers, custom queries and extensions"
git push -u origin feature/bank-transfer
```

Verify that source, templates, CSS, tests and documentation are included. `target/`, local database files, tool downloads and `secrets.properties` must be excluded. A password must not appear in the staged diff.

On GitHub, create a pull request with base `main` and compare `feature/bank-transfer`.

**Title:** Add bank transfer with @Transactional and @Query

Suggested description, adjusted to match the actual recorded checks:

> Completes the bank transfer application with conditional debit and credit queries, a service transaction covering balances and the success record, and a separate REQUIRES_NEW audit for failed attempts. Adds validation, customer search/history, successful large-transfer filtering, cash deposits/withdrawals and a 1% bank fee. Includes isolated integration tests and Postman collections for the original zero-fee examples and final extensions.

Review the **Files changed** tab and the recorded test results. Merge the reviewed pull request, then update local `main`:

```powershell
git switch main
git pull --ff-only origin main
git log --oneline -5
```

Retain screenshots or links showing the repository URL, feature branch, pull request changes and merged state for Task 11.
