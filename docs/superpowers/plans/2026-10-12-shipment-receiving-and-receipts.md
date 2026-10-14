# Shipment Receiving and Receipts Implementation Plan

> **For agentic workers:** Execute inline task-by-task after Phase 1 passes. Use real Room state in Robolectric tests and verify the specified command at each checkpoint.

**Goal:** Connect shipment line items to received inventory, allocate landed costs in UGX, and provide useful offline sale receipts for cash and credit transactions.

**Architecture:** Add shipment lines that reference existing inventory items and record ordered/received quantities, source currency, and a user-confirmed exchange rate. Receive stock through a transaction service that updates weighted-average per-unit cost, records an inventory/AP journal transaction, and prevents duplicate application. Generate receipts locally from saved sale/payment data and share them through Android's document/share APIs.

**Tech Stack:** Java 8, Room 2.6.1, Android Gradle Plugin 8.2.2, Robolectric, Android `PdfDocument`, AndroidX FileProvider.

**Spec:** `docs/superpowers/specs/2026-10-12-finance-reliability-upgrade-design.md`

## Global Constraints

- Preserve Room schema version 4 data and use explicit non-destructive migration to version 5.
- Exchange rates are entered and confirmed by the user; store the rate on each line.
- Allocate shipment-level freight, insurance, duty, VAT, clearing, and local transport proportionally by converted invoice value.
- Receiving each quantity and its stock/journal/payment side effects is atomic and idempotent.
- Keep receipt generation available offline and do not put secrets or customer data in logs.
- Keep all financial amounts in UGX after conversion; retain source currency and rate for audit.

## File Map

- `FinanceApp/app/src/main/java/com/financeapp/uganda/models/ShipmentLine.java`: ordered/received quantities, item link, source price/currency/rate, allocated charges, quantity already stocked.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/models/Shipment.java`: receipt state, received time, and linked payable/journal IDs.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/database/AppDatabase.java`: version 5 and migration 4 to 5.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/database/FinanceDao.java`: shipment-line queries and non-void landed inventory totals.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/accounting/FinanceTransactionService.java`: add shipment lines and receive pending quantities atomically.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/ShipmentsActivity.java`: add/edit lines, receive selected quantities, show allocation preview.
- `FinanceApp/app/src/main/res/layout/dialog_add_shipment_line.xml`: item, ordered/received quantity, foreign unit price, currency, and exchange rate.
- `FinanceApp/app/src/test/java/com/financeapp/uganda/accounting/FinanceTransactionServiceTest.java`: landed-cost and receiving integration tests.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/receipts/SaleReceiptGenerator.java`: local receipt rendering and sharing.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/AddSaleActivity.java`, `SalesActivity.java`: create/share receipt after sale and re-share from history.
- `FinanceApp/app/src/main/AndroidManifest.xml`, `FinanceApp/app/src/main/res/xml/file_paths.xml`: restricted FileProvider for generated receipts.

## Tasks

### Task 1: Add Shipment Lines and Non-Destructive Migration

- [ ] Add a v4 to v5 migration test seeded from exported schema version 4; assert sales, expenses, inventory, shipment aggregates, and accounting rows survive.
- [ ] Add `ShipmentLine` entity with shipment ID, inventory item ID, ordered quantity, confirmed received quantity, already-stocked quantity, source unit cost, currency code, exchange rate, and allocated landed cost.
- [ ] Add shipment `stockReceivedAt`, `payableId`, and journal entry link with SQL defaults matching Room nullability.
- [ ] Add migration 4 to 5, register it in Room, export schema 5, and extend `FinanceDao` for lines and shipment lookups.
- [ ] Run `gradlew.bat testDebugUnitTest` from `FinanceApp`; verify migration preservation before adding UI.

### Task 2: Add Idempotent Receiving and Cost Allocation

- [ ] Add failing tests for UGX-only line cost, foreign-currency conversion, proportional allocation across two products, weighted-average existing stock cost, partial receipt, duplicate receive, invalid exchange rate, and injected journal failure rollback.
- [ ] Implement `addShipmentLine` validation: item exists, ordered quantity > 0, received quantity between 0 and ordered, unit price >= 0, currency supported, exchange rate > 0, shipment not fully received.
- [ ] Implement `receiveShipment` in one Room transaction: apply only `receivedQuantity - quantityAlreadyStocked`, compute source-cost value in UGX, allocate shipment charges proportionally by line invoice value, blend each cost component by old versus received quantity, increment stock once, and persist updated stocked quantity.
- [ ] Post inventory debit and linked accounts-payable credit for each received batch using the Phase 1 transaction/journal helper; update the shipment received timestamp only when all line quantities are stocked.
- [ ] Reject duplicate receipts, negative/invalid costs, missing lines, and any write failure without partial stock, payable, or journal changes.
- [ ] Add a receiving dialog and shipment detail actions; show per-item goods value, charge allocation, landed cost per unit, quantity received, and quantity remaining before confirmation.
- [ ] Run the focused shipment test methods, then `gradlew.bat testDebugUnitTest`.

### Task 3: Add Offline Sale Receipts and Final Verification

- [ ] Add a failing test for a generated receipt containing sale number/date, item, quantity, unit price, discount, gross/paid/balance, customer, payment method, and payment history.
- [ ] Implement `SaleReceiptGenerator` using `PdfDocument`; render text with page bounds, sanitize filenames, and keep output in app cache.
- [ ] Configure an exported, grant-only `FileProvider` path restricted to the receipts cache directory; share with `ACTION_SEND` and `FLAG_GRANT_READ_URI_PERMISSION`.
- [ ] Add “Save/share receipt” after a successfully committed sale and a re-share action in sales history. Receipt failure must not roll back or duplicate the sale.
- [ ] Run receipt tests and the full `gradlew.bat testDebugUnitTest` plus `gradlew.bat assembleDebug`.
- [ ] If an authorized device is available, add two disposable products, receive a shipment, verify stock/landed cost/payable totals, record a deposit sale, share its receipt, then restore the pre-test database.