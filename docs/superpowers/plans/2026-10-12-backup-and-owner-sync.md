# Backup and Owner Sync Implementation Plan

> **For agentic workers:** Execute inline in order. Do not begin Supabase sync until encrypted local backup/restore passes its acceptance checks.

**Goal:** Protect local finance records with restorable encrypted backups, then sync one owner's data across devices without weakening offline operation or financial auditability.

**Architecture:** Room remains the offline source of truth. Backups use a user-held passphrase and AES-GCM around a versioned archive created only after a WAL checkpoint. Sync adds an idempotent local outbox and sends authenticated owner-scoped transactions to Supabase; financial writes use server-side RPCs and row-level security, not last-write-wins replacement.

**Tech Stack:** Java 8, Room 2.6.1, Android Storage Access Framework, Java Cryptography Architecture (PBKDF2-HMAC-SHA256 + AES-256-GCM), WorkManager, OkHttp 4.12, Supabase Auth/PostgREST/Postgres RPC.

**Spec:** `docs/superpowers/specs/2026-10-12-finance-reliability-upgrade-design.md`

## Global Constraints

- Preserve Room version 5 records and use explicit non-destructive migrations.
- Never log backup passphrases, Supabase tokens, customer data, or financial payloads.
- Never embed the Supabase service-role key; Android uses only the public client key plus an authenticated user token.
- Financial transactions are append-only; corrections sync as reversals, never last-write-wins edits.
- Offline sale/expense/receiving flows remain usable without network access.
- Keep cloud setup disabled and clearly report “not configured” until valid local configuration is present.
- Do not claim two-device sync is verified without a provisioned test project and two authenticated devices.

## File Map

### Backup

- `FinanceApp/app/src/main/java/com/financeapp/uganda/database/AppDatabase.java`: safe database close and WAL checkpoint access.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/backup/FinanceBackupManager.java`: encrypted export/import and archive validation.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/backup/BackupFormat.java`: magic/version/KDF and cipher metadata.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/SettingsActivity.java`: export/restore actions and passphrase confirmation.
- `FinanceApp/app/src/main/res/layout/activity_settings.xml`: backup controls and last-backup state.
- `FinanceApp/app/src/test/java/com/financeapp/uganda/backup/FinanceBackupManagerTest.java`: encryption, corruption rejection, and restore preservation.

### Sync

- `FinanceApp/app/build.gradle`: WorkManager dependency and BuildConfig/local configuration fields.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/models/SyncOutboxEntry.java`: durable unique operation ID, aggregate type/ID, JSON payload, timestamps, retry state.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/database/AppDatabase.java`: version 6 migration and outbox entity.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/database/FinanceDao.java`: enqueue, pending, retry, acknowledge, and conflict queries.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/sync/SupabaseConfig.java`: validate local URL/public key and disabled state.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/sync/SupabaseAuthClient.java`: sign-in, refresh, and sign-out via HTTPS Auth endpoints.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/sync/FinanceSyncClient.java`: owner-scoped sync, idempotent RPC requests, response validation.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/sync/FinanceSyncWorker.java`: WorkManager retry/backoff and outbox draining.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/accounting/FinanceTransactionService.java`: enqueue sync operations inside the same Room transaction as financial mutations.
- `supabase/migrations/20261012_owner_sync.sql`: owner-scoped tables, RLS, operation deduplication, stock-delta RPC, and atomic transaction RPCs.
- `FinanceApp/app/src/main/java/com/financeapp/uganda/SettingsActivity.java`: owner sign-in/out and sync status.
- `README.md`: local Supabase configuration, data model, and device-pairing/test procedure.
- `FinanceApp/app/src/test/java/com/financeapp/uganda/sync/FinanceSyncClientTest.java`: fake HTTP server tests for auth errors, idempotency, retry, and conflicts.

## Tasks

### Task 1: Create Backup/Restore Contract Tests

- [ ] Add tests that export a Room database containing inventory, sale/deposit, later receivable payment, expense, shipment lines/receipt, and journal data.
- [ ] Assert archive has a versioned header and encrypted payload; raw output must not contain sentinel customer names or descriptions.
- [ ] Assert a wrong passphrase, truncated archive, unknown format version, invalid GCM tag, and invalid SQLite database are rejected without replacing live data.
- [ ] Assert correct restore reproduces the same primary keys, stock, paid/due balances, shipment receipts, and trial balance.
- [ ] Run only `gradlew.bat :app:testDebugUnitTest --tests "com.financeapp.uganda.backup.FinanceBackupManagerTest"`; keep failures at expected assertions.

### Task 2: Implement Safe Encrypted Backup/Restore

- [ ] Add `FinanceBackupManager` format: fixed magic/version, 16-byte random salt, 12-byte random GCM nonce, bounded PBKDF2 iteration count, and AES-256-GCM authenticated ciphertext.
- [ ] Archive `finance_database` only after `PRAGMA wal_checkpoint(FULL)` and closing Room; include a manifest with app version, Room schema version, creation time, and ciphertext digest.
- [ ] Export through `ACTION_CREATE_DOCUMENT`; write to a private temporary file first, fsync it, then stream encrypted bytes to the selected document URI.
- [ ] Restore through `ACTION_OPEN_DOCUMENT`; decrypt to a private temp file, validate manifest/version/integrity, open and schema-validate the candidate database, and create a pre-restore snapshot before swapping.
- [ ] Replace live DB only with the app force-stopped and Room singleton closed; on any failure retain/restore the pre-restore database. Reopen via Room with registered migrations.
- [ ] Add confirmation/re-entry for passphrase and restore warnings; show progress/errors without printing sensitive values.
- [ ] Run backup tests, full local tests, and debug build; manually export and restore a disposable database on the tablet.

### Task 3: Add Owner Sync Schema and Local Outbox

- [ ] Add migration 5 to 6 and test preservation of all v5 records plus creation of an empty outbox.
- [ ] Define outbox operations for sale, expense, reversal, receivable/payable invoice/payment, shipment line, shipment receipt, and inventory adjustment. Every operation has a UUID generated before commit.
- [ ] Enqueue each operation inside the same `runInTransaction` as its local financial mutation; do not serialize unrelated rows into silent last-write-wins snapshots.
- [ ] Include aggregate IDs and explicit schema version in payloads; store attempts, next retry time, acknowledgement time, and terminal conflict/error state.
- [ ] Add DAO tests proving rollback removes both business mutation and outbox entry, and duplicate operation IDs cannot be enqueued twice.

### Task 4: Add Supabase Owner Authentication and Atomic Sync

- [ ] Add local-only Gradle properties for project URL and public anon key; missing/invalid configuration keeps Sync Settings disabled. Do not commit secrets.
- [ ] Implement email/password sign-in, refresh, and sign-out; keep refresh tokens encrypted at rest and never put credentials in BuildConfig or logs.
- [ ] Add Postgres tables with `owner_id`, RLS policies tied to `auth.uid()`, unique idempotency keys, and no client access to service-role functions.
- [ ] Add Postgres RPCs that apply one operation and all related journal/AR/AP/stock rows in one database transaction; include stock version/delta guards for concurrent offline receipts.
- [ ] Implement OkHttp client with TLS-only base URL, bounded timeouts, auth header refresh, request ID idempotency, response schema checks, and redacted error messages.
- [ ] Implement WorkManager drain with connectivity constraints, exponential backoff, idempotent acknowledgement, and visible conflict state for stock that would become negative.
- [ ] Add Robolectric/fake-server tests for 401 refresh, timeout retry, duplicate ACK, rejected cross-owner data, operation ordering, and conflict surfacing.

### Task 5: Add Settings UI and Verify Phase

- [ ] Add Backup/Restore controls, owner sign-in/out, last successful sync, pending count, and unresolved conflict count.
- [ ] Keep sync status actionable: offline, needs sign-in, setup missing, syncing, pending, conflict, or up to date.
- [ ] Run the full `gradlew.bat :app:testDebugUnitTest` and `gradlew.bat :app:assembleDebug`.
- [ ] Verify backup/restore on the tablet with disposable data and prove pre-restore state can be recovered.
- [ ] Once the user provisions Supabase URL/public anon key locally, deploy the SQL migration to that project and test with two owner devices: online round trip, offline writes/retry, app restart, duplicate delivery, stock conflict, and owner isolation.
- [ ] Do not mark live sync complete until all two-device tests pass; no Supabase project is currently configured in this workspace.

## Phase Gate

Phase 3 is complete only when encrypted backup/restore passes its local and tablet checks, sync unit tests pass, and a provisioned Supabase project passes two-device offline/online and isolation checks. Without the project, code/config preparation may be complete but live sync remains unverified.
