# Finance Reliability Upgrade Design

**Status:** Draft for user review  
**Date:** 2026-10-12

## Goal

Upgrade Kistie Finance from a single-device tracker into a dependable offline-first finance app for the owner's Kampala retail and import business. Preserve current local records, connect operational activity to inventory and double-entry accounting, and add owner-only sync and recoverable backups.

## Verified Current-State Gaps

- `AddSaleActivity` inserts sales and decrements stock directly instead of posting through `AccountingService`.
- Household and business expense screens insert expenses directly instead of using `ExpenseAccountingHelper`.
- Deleting a sale does not restore stock or reverse a journal entry.
- Shipment records have aggregate values and statuses but no received product lines to update stock or calculate per-item landed cost.
- Credit sale payment is not offered in the sale form, despite existing receivables models and accounting helpers.
- Room uses `fallbackToDestructiveMigration`; an upgrade can erase local records. The app has no configured automatic backup or sync backend.
- There are no existing app test source sets.

## Phase 1: Atomic Transactions and Auditability

### Design

Introduce a transaction-oriented domain service that owns business writes and uses `RoomDatabase.runInTransaction` for each complete operation.

- **Sale:** validate item, quantity, unit price, discount, payment method, and stock; write the sale; decrement stock; post revenue, cash/receivable, COGS, and inventory journal lines; link generated journal/receivable records. All writes commit or roll back together.
- **Expense:** validate amount, category, date, and payment method; write the expense; post its journal entry and any payable record together.
- **Edit:** preserve the original posted transaction in the audit trail, reverse its financial and stock effects, and post the corrected transaction atomically.
- **Void/reversal:** do not hard-delete posted financial records. Mark them voided with reason/time and post reversing journal lines; restore stock for a voided sale.
- Apply the same orchestration to receivable/payable payments and expense update/delete paths.
- Add edit actions to sales history and household/business expense records; use the same validation and reversal flow as the transaction service.

Household spending remains separately reportable and must not be counted as business operating expense/profit. When paid from a business cash/bank account, post it to an owner-draw/equity account; business expenses post to operating expense. This assumes household records are tracked for the owner's overall view, not as shop costs.

Validation rejects non-positive quantities and amounts, negative prices/discounts, discounts greater than the gross sale, insufficient stock, invalid dates, and already-voided edits. UI messages should identify the field and correction needed.

### Legacy records

Existing sales and expenses may have no journal entries because the current screens bypass the accounting helpers. Do not silently synthesize entries during a schema migration: that can duplicate or misstate balances. Add a reconciliation report/count in the upgrade flow and require explicit review before any historical journal backfill. Existing inventory and transaction records must remain untouched by the migration itself.

### Tests

Add automated tests for valid sale/expense posting, balanced journal lines, stock updates, invalid-input rejection, insufficient-stock rejection, rollback after an injected persistence failure, edits, voids/reversals, and receivable/payable payments. Add Room migration tests that prove pre-upgrade records survive. Prefer Room in-memory tests and the repo's current Android/Java toolchain; no test source set currently exists.

## Phase 2: Receiving, Landed Cost, Credit, and Receipts

### Shipment receiving and landed cost

Add shipment product lines with inventory-item link, ordered and received quantity, invoice unit cost, source currency, and manually confirmed exchange rate. Keep shipment-level freight, duty, and clearing charges. On receipt, allocate those charges across received lines in proportion to their UGX-converted invoice value by default; show the allocation and per-unit landed cost for confirmation. Receiving is idempotent and updates stock and accounting in one transaction. Partial receipts are supported; a shipment cannot be marked received twice for the same quantity.

### Credit and receipts

At checkout, let the user enter the amount received now (deposit) and its method, then calculate the remaining balance. For example, a UGX 100,000 sale with a UGX 30,000 deposit records UGX 30,000 received and UGX 70,000 outstanding. Validate that the deposit is between zero and the sale total; a fully paid sale has no open receivable. A positive balance creates a sale-linked receivable and due date in the same transaction.

Record each deposit and later partial payment with amount, date, method, and reference so the customer account shows original total, amount paid, and current balance due. Post the initial sale as debit to the selected cash/bank/mobile-money account for the deposit plus debit to receivables for the remainder, credit revenue for the full sale, and post COGS/inventory reduction. Subsequent payments debit the selected asset account and credit receivables atomically.

Generate a receipt with business, sale number/date, item quantities, unit prices, discount, total, deposit/payment details, and balance due. Support Android share/print flows; receipt output must not require a network connection.

## Phase 3: Backup and Owner-Only Sync

### Local-first operation

Room remains the on-device working database so sales and expenses remain available offline. Queue syncable operations durably and retry through Android WorkManager when connectivity returns. Use stable operation IDs for idempotent retries.

### Supabase

Use Supabase Auth for one owner account across the owner's devices, and Postgres tables protected by row-level security. Scope each business dataset to its authenticated owner. The Android app must use only public client configuration plus authenticated user tokens; never embed a Supabase service-role secret. Store project URL/public key in local build configuration, not committed source.

Apply each remote sale, expense, payment, receive, or reversal through a server-side Postgres function (RPC) so related cloud rows commit atomically; do not sync multi-table financial writes as unrelated client REST calls.

Financial transactions are append-only after posting; corrections sync as reversals and replacement entries, not last-write-wins overwrites. Sync detects concurrent stock changes that would make stock negative and surfaces a resolution state instead of silently discarding a sale or overwriting inventory. Do not add staff accounts or role management in this phase.

### Recovery

Provide an explicit encrypted export/restore flow using Android's document picker. The backup includes the database and a versioned manifest; verify the archive before replacing live data and keep a pre-restore snapshot. Cloud sync is not a substitute for a restorable backup. Use a user-held passphrase for portable encryption so recovery does not depend solely on one device's Android Keystore.

## Acceptance Gates

1. A Room schema upgrade preserves existing rows and never uses destructive fallback.
2. Each sale/expense/payment operation is all-or-nothing across business records, stock, receivables/payables, payment history, and journal records.
3. Trial balance remains balanced after all supported operations, edits, and reversals.
4. Automated tests cover validation, rollback, migration, and accounting invariants.
5. Tablet smoke tests cover receiving, sale, credit payment, receipt sharing, backup restore, and offline retry.
6. Two-device sync tests prove owner isolation, idempotent retries, offline recovery, and visible conflict handling. This gate requires a user-provisioned Supabase project and test account; no cloud project or credentials currently exist in the workspace.

## Scope and Risks

- No multi-staff accounts, payroll, tax filing, automatic FX-rate service, or external payment processor integration in this upgrade.
- Exchange rates are entered and confirmed by the user; each transaction retains the rate used.
- Backfilling historical journal entries requires explicit reconciliation because current operational screens did not consistently create them.
- Sync conflict behavior and backup restore are financial-data safety boundaries and must not be released without the acceptance tests above.
- No cloud credentials, secrets, or live customer data are to be added to source control or test fixtures.