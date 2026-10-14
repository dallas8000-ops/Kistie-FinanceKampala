# Finance Transaction Integrity Implementation Plan

> **For agentic workers:** Execute inline task-by-task against the approved spec. Keep each test cycle focused and verify its command before proceeding.

**Goal:** Make sales, deposits, expenses, edits, payments, and reversals atomic across Room business records, inventory, receivables/payables, and journal entries.

**Architecture:** A `FinanceTransactionService` owns financial mutations and wraps each operation in `AppDatabase.runInTransaction`. Screens collect and display data but do not write financial records directly. Posted changes are corrected with auditable reversals; database upgrades use explicit migrations.

**Tech Stack:** Java 8, Android Gradle Plugin 8.2.2, Room 2.6.1, JUnit 4, Robolectric, AndroidX Room Testing.

**Spec:** `docs/superpowers/specs/2026-10-12-finance-reliability-upgrade-design.md`

## Global Constraints

- Preserve `minSdk 24`, `compileSdk 34`, Java 8, and Room 2.6.1.
- Never use destructive migration; preserve every existing device record.
- Do not silently backfill journal entries for legacy sales or expenses.
- Posted edits and voids retain audit history and reverse accounting/inventory effects.
- Household spending is excluded from business operating profit; when paid from business funds it posts to Owner Drawings.
- Preserve the existing UI changes and approved spec in the current workspace.
- Do not commit or push unless explicitly requested.

## File Map

- `FinanceApp/app/build.gradle`: JVM Android test dependencies and Android-resource test configuration.
- `FinanceApp/app/schemas/`: exported Room schema snapshots for migration tests.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/database/AppDatabase.java`: database version, migrations, and safe builder configuration.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/database/FinanceDao.java`: transaction lookup, status filters, payment history, and reconciliation queries.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/models/Sale.java`, `Expense.java`, `AccountReceivable.java`, `AccountPayable.java`: posted/void metadata and paid/balance state.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/models/ReceivablePayment.java`: immutable deposit and later payment records.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/accounting/AccountCodes.java`, `AccountSeeder.java`: stable Owner Drawings account and idempotent seeding.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/accounting/FinanceTransactionService.java`: validation and atomic sale, expense, payment, edit, and reversal operations.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/AddSaleActivity.java`, `SalesActivity.java`: submit sale/deposit through service; edit/void history actions.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/HouseholdActivity.java`, `BusinessActivity.java`: expense save/edit/void through service.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/ReceivablesActivity.java`, `PayablesActivity.java`: atomic invoices/payments and balance validation.
- `FinanceApp/app/src/test/java/com/financeapp/uganda/accounting/FinanceTransactionServiceTest.java`: in-memory Room integration tests under Robolectric.
- `FinanceApp/app/src/test/java/com/financeapp/uganda/database/FinanceDatabaseMigrationTest.java`: version 3 to 4 migration preservation test.

## Tasks

### Task 1: Add the Room Test Harness and Capture Current Failures

**Files:**
- Modify `FinanceApp/app/build.gradle`.
- Modify `FinanceApp/app/src/main/java/com/financeapp/uganda/database/AppDatabase.java` for schema export only.
- Create `FinanceApp/app/src/test/java/com/financeapp/uganda/accounting/FinanceTransactionServiceTest.java`.
- Generate `FinanceApp/app/schemas/` snapshot for database version 3.

**Test fixture:** Use Robolectric with `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class).allowMainThreadQueries().build()`. Seed the real chart of accounts and a real stock item; do not mock the DAO. Set the private `AppDatabase.instance` to the in-memory database through reflection, and restore/close it in teardown.

- [ ] Add test dependencies: JUnit 4.13.2, AndroidX Test Core 1.5.0, Room Testing 2.6.1, Robolectric 4.11.1.
- [ ] Set `testOptions.unitTests.includeAndroidResources = true` and configure the Room annotation processor's `room.schemaLocation`.
- [ ] Export the current version 3 schema and run `gradlew.bat :app:assembleDebug` from `FinanceApp`.
- [ ] Add `saleSubmissionCreatesJournalAndDecrementsStock`: launch `AddSaleActivity`, select an item with quantity 4, unit cost UGX 500 and selling price UGX 1,200, record one unit, then assert quantity 3, total UGX 1,200, COGS UGX 500, and a linked balanced journal. Against current code, the journal assertion must fail.
- [ ] Add `failedExpensePostingRollsBackExpenseAndJournal`: install a SQLite trigger that aborts journal-line insertion, call the current `ExpenseAccountingHelper.saveNew`, and assert expense and journal counts remain zero. Against current code, the expense/journal rows remain and the assertions must fail.
- [ ] Run `gradlew.bat :app:testDebugUnitTest` from `FinanceApp`; verify failures occur at those behavior assertions, not compilation or setup.

### Task 2: Add a Version 4 Non-Destructive Migration

**Files:** `AppDatabase.java`, `FinanceDao.java`, models `Sale.java`, `Expense.java`, `AccountReceivable.java`, `AccountPayable.java`, new `ReceivablePayment.java`, `AccountCodes.java`, `AccountSeeder.java`, and new `FinanceDatabaseMigrationTest.java`.

**Schema contract:** Existing sale/expense rows default to status `POSTED`. Sale stores the amount paid at checkout separately from its full `totalAmount`. Receivable payment history stores receivable ID, amount, method, date, reference, and journal entry ID. Owner Drawings gets a stable, unused account ID.

- [ ] Add an explicit `Migration 3 -> 4` with SQL defaults for new non-null fields; preserve IDs and values.
- [ ] Add the payment-history table and the Owner Drawings account idempotently for both new and already-populated databases.
- [ ] Replace `fallbackToDestructiveMigration()` with explicit migration registration; do not erase data when a migration is missing.
- [ ] Test migration from the checked-in v3 schema containing a sale, expense, inventory item, and account; assert rows survive and status defaults to `POSTED`.
- [ ] Export and retain the v4 schema snapshot; run `gradlew.bat :app:testDebugUnitTest` from `FinanceApp`.

### Task 3: Implement Atomic Sale, Deposit, and Receivable Operations

**Files:** Create `accounting/FinanceTransactionService.java`; modify `AccountingService.java`, `FinanceDao.java`, and `FinanceTransactionServiceTest.java`.

**Public API:**

```java
long recordSale(Sale sale, double amountPaid, String paymentMethod, Date dueDate);
void recordReceivablePayment(int receivableId, double amount, String paymentMethod, Date paidAt, String reference);
void voidSale(int saleId, String reason, boolean refundConfirmed);
```

- [ ] Add failing tests for cash sale, partial deposit, full credit, overpayment, negative deposit, insufficient stock, invalid quantity, and journal-write rollback.
- [ ] Implement `recordSale` in `database.runInTransaction`: insert sale, decrement stock, post balanced journal, and create/link a receivable only when balance remains.
- [ ] Validate positive quantity, non-negative unit price, discount not above gross, `0 <= amountPaid <= totalAmount`, existing item, sufficient stock, valid payment method, and valid due date for credit.
- [ ] Post deposit to its selected cash/bank/mobile-money account, remaining amount to receivables, full amount to revenue, and COGS/inventory using `InventoryItem.getTotalCost()`.
- [ ] Store receivable `amount` as the full sale total and `amountPaid` as checkout deposit; a fully paid sale has no open receivable.
- [ ] Record later receivable payments, journal lines, paid total, and status together; require `0 < payment <= balanceDue`.
- [ ] Void idempotently: restore stock; reverse the original sale journal and every linked later receivable-payment journal; mark sale/receivable void; retain reason/time; require refund confirmation and post refunds back through original payment accounts.
- [ ] Run the focused test class and verify all validation and rollback tests pass.

### Task 4: Route Sale Entry and History Through the Service

**Files:** `AddSaleActivity.java`, `SalesActivity.java`, `activity_add_sale.xml`, `SaleAdapter.java`, `FinanceDao.java`, and `FinanceTransactionServiceTest.java`.

- [ ] Add checkout amount received, payment method, and calculated balance due; default unit price from inventory and show total before saving.
- [ ] Replace direct sale insert and inventory update with `recordSale`; keep the form open with a field-level message on validation failure.
- [ ] Add edit and void actions in sale history. Edits create a reversal and replacement atomically; voids require a reason/refund confirmation and never hard-delete posted sales.
- [ ] Exclude voided records from sales/profit totals while preserving them in audit history.
- [ ] Assert a UGX 100,000 sale with UGX 30,000 deposit produces a UGX 70,000 receivable, correct stock, and balanced journals.
- [ ] Run `gradlew.bat :app:testDebugUnitTest` from `FinanceApp`.

### Task 5: Route Household and Business Expenses Through the Service

**Files:** `ExpenseAccountingHelper.java`, `AccountingService.java`, `HouseholdActivity.java`, `BusinessActivity.java`, `ExpenseAdapter.java`, `FinanceDao.java`, and `FinanceTransactionServiceTest.java`.

- [ ] Add failing tests for business expense posting, household expense posting to Owner Drawings, edit/reversal, invalid amounts, and journal-write rollback.
- [ ] Save/edit/void the expense, payable, journal, and status in one Room transaction.
- [ ] Replace direct expense insert/update/delete from both screens with transaction-service calls; add user-facing edit/void actions.
- [ ] Map household category to Owner Drawings and business categories to Operating Expenses; exclude household spending from business operating profit.
- [ ] Validate positive amount, required description/category, supported payment method, and non-voided state.
- [ ] If a payable has payments, reverse linked payment entries and require vendor-refund confirmation before voiding.
- [ ] Run the focused transaction test class and verify the trial balance remains balanced.

### Task 6: Make Receivables and Payables Atomic

**Files:** `ReceivablesActivity.java`, `PayablesActivity.java`, `FinanceTransactionService.java`, `AccountingService.java`, `FinanceDao.java`, and `FinanceTransactionServiceTest.java`.

- [ ] Add failing tests for partial/full AR and AP payments, excessive/negative amounts, and journal-write rollback.
- [ ] Move manual receivable/payable creation, payment, edit, and void into the transaction service; do not delete posted records without reversal.
- [ ] Display original amount, paid amount, outstanding balance, and payment history.
- [ ] Ensure each payment history row links to one balanced journal and updates open/partial/paid status atomically.
- [ ] Run focused tests and verify hand-calculated balances and trial-balance totals.

### Task 7: Reconcile Legacy Records and Verify the Phase

**Files:** `FinanceDao.java`, `TrialBalanceActivity.java`, `activity_trial_balance.xml`, and `README.md`.

- [ ] Count posted legacy sales/expenses without matching journal entries; never auto-post them.
- [ ] Display a reconciliation warning and require review before any historical backfill.
- [ ] Document migration preservation, validation, reversal behavior, and legacy reconciliation.
- [ ] Run `gradlew.bat :app:testDebugUnitTest` and `gradlew.bat :app:assembleDebug` from `FinanceApp`.
- [ ] If a device is connected, smoke-test sale, deposit, expense, edit, void, and partial receivable payment with disposable data; restore the pre-test database afterward.
- [ ] Compare inventory, receivable balances, and trial balance to hand-calculated values; stop on any mismatch.

## Later Plans

After Phase 1 passes, create separate plans for shipment lines/partial receiving/landed-cost allocation/receipts, then encrypted backup/restore and owner-only Supabase sync. Sync acceptance requires a user-provisioned Supabase project and locally configured public client settings; no cloud project or credentials exist in the workspace.
