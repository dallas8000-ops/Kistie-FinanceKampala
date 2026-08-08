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
