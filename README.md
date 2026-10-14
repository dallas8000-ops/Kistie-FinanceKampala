# Finance Uganda - Android App

Household & retail finance manager for Kampala clothing business with Turkey/China import tracking.

## Install on Phone

1. Copy `FinanceUganda.apk` to your Android phone (USB, email, or cloud).
2. On the phone, open the APK file.
3. Allow "Install from unknown sources" if prompted.
4. Tap Install.

**Minimum Android:** 7.0 (API 24)

## Project Location

- **Source code:** `FinanceApp/`
- **Ready-to-install APK:** `FinanceUganda.apk` (project root)
- **Debug APK (from build):** `FinanceApp/app/build/outputs/apk/debug/app-debug.apk`

## Features

- **Dashboard** – Monthly sales, profit, expenses, inventory value
- **Household Expenses** – Rent, utilities, food, school fees, etc.
- **Business Operations** – Shop expenses by category (operational, rent, staff, marketing)
- **Inventory Control** – Stock, costs, selling prices, low-stock alerts
- **Import Shipments** – Track Turkey/China shipments to Uganda
- **Sales** – Record sales and auto-update stock
- **Reports** – Charts and financial summaries

## Transaction Safety

- Sales, deposits, expenses, supplier/customer payments, shipment receipts, edits, and voids post through Room transactions.
- Voided transactions remain in history with reversing journal entries; they are excluded from active sales/expense totals.
- Room upgrades preserve existing records. Older sales or expenses without journal entries are counted for review on Trial Balance and are not silently backfilled.
- Exported schema and Robolectric migration/transaction tests are under `FinanceApp/app/schemas/` and `FinanceApp/app/src/test/`.

## Shipment Receiving and Receipts

- Shipment product lines retain invoice currency and the confirmed UGX exchange rate.
- Partial receipts update stock once, allocate freight/duty/clearing costs, create a supplier payable, and show landed unit cost.
- Sale receipts are generated offline as PDFs and shared through Android's share sheet.

## Local OpenAI Key for a Private Build

The assistant can load its key from the tablet's Settings screen or from the ignored `FinanceApp/local.properties` file:

```properties
openai.apiKey=YOUR_OPENAI_API_KEY
```

Then rebuild with `gradlew.bat assembleDebug` from `FinanceApp`. A key supplied this way is compiled into the APK and can be extracted from it; use only for a private, trusted build and never share or commit that APK or `local.properties`. A device-saved key takes precedence over the build key.

## Rebuild APK

```bat
cd FinanceApp
gradlew.bat assembleDebug
```

Release APK:

```bat
gradlew.bat assembleRelease
```

## Open in Android Studio (optional)

File → Open → select the `FinanceApp` folder.
