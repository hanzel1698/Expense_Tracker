# Expense Tracker

A brutalist-styled Android expense tracker built with Kotlin and Jetpack Compose. Track daily spending, manage budgets by category, set recurring expenses, and sync data across devices via Supabase or Google Drive.

## Features

- **Dashboard** — calendar view, expense summaries, recent transactions, budget overview, and category/monthly/year trend charts
- **Expense management** — add, edit, delete, search, and filter expenses; group by store or line item
- **Budgets** — allocate budgets by category and subcategory with unallocated balance tracking
- **Recurring expenses** — define repeating charges with automatic generation
- **Categories, subcategories & labels** — fully customizable taxonomy in Settings
- **Supabase sync** — push/pull expenses, recurring items, and app settings (manual sync, last-write-wins)
- **Google Drive sync** — backup and restore expense data to a Drive app folder
- **Brutalist UI** — high-contrast black/white design with bold typography

## Requirements

- **Android Studio** Ladybug (2024.2+) or newer with Android SDK 36
- **JDK 11+**
- **Android device or emulator** running API 33 (Android 13) or higher
- *(Optional)* Supabase project and Google Cloud OAuth credentials for cloud sync

## Run from source

1. Clone the repository:
   ```bash
   git clone https://github.com/hanzel1698/Expense_Tracker.git
   cd Expense_Tracker
   ```

2. Open the project in Android Studio and let Gradle sync.

3. Connect a device or start an emulator (API 33+).

4. Run the **app** configuration, or from the project root:
   ```bash
   ./gradlew installDebug
   ```

## Build

Debug APK:
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

Release APK (signed with debug keystore for local testing):
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Publish / release

Use the repo-relative build script:
```powershell
.\tools\build-release.ps1
```

This produces a release APK under `dist/`. GitHub Releases attach the built APK automatically via CI or `gh release create`.

For production signing, configure a release keystore in `app/build.gradle.kts` and store credentials outside the repo.

## Project structure

```
ExpenseTracker2/
├── app/                          # Main Android application module
│   └── src/main/java/com/example/expensetracker/
│       ├── MainActivity.kt       # Navigation, dashboard, settings
│       ├── data/                 # DataRepository, RecurringExpenseEngine
│       ├── model/                # Expense, RecurringExpense
│       ├── sync/                 # SupabaseService, SyncService, Google Drive
│       └── ui/
│           ├── components/       # BrutalistCalendar, BrutalistComponents
│           ├── screens/          # BudgetScreen, ExpenseEntryScreen
│           └── theme/            # Colors, typography, Material theme
├── gradle/                       # Version catalog (libs.versions.toml)
├── tools/                        # build-release.ps1
└── .github/workflows/            # CI build workflow
```

## Sync configuration

### Supabase

Supabase URL and anon key are configured in `SupabaseService.kt`. Tables used: `expenses`, `recurring_expenses`, `app_settings`.

### Google Drive

Place your OAuth client secret JSON at:
```
app/client_secret_<your-client-id>.apps.googleusercontent.com.json
```
This file is gitignored. Update `app/src/main/res/values/google-services.xml` with your API key if needed.

## License

Private project — all rights reserved.
