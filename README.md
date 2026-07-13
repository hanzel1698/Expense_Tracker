# Expense Tracker

An Android expense tracker built with Kotlin and Jetpack Compose. Track daily spending, manage budgets by category, set recurring expenses, and back up data to Google Drive.

The UI is **Aurora** — a Material 3 design with a teal/emerald palette, rounded tonal cards, and full light/dark theming (`ui/modern/`). `ModernMainActivity` is the app's launcher activity.

## Features

- **Dashboard** — calendar view, expense summaries, recent transactions, budget overview, and category/monthly/year trend charts
- **Expense management** — add, edit, delete, search, and filter expenses; group by store or line item
- **Budgets** — allocate budgets by category and subcategory with unallocated balance tracking
- **Recurring expenses** — define repeating charges with automatic generation
- **Categories, subcategories & labels** — fully customizable taxonomy in Settings
- **Google Drive sync** — backup and restore expense data to a Drive app folder
- **Aurora UI** — Material 3 design with light and dark themes

## Requirements

- **Android Studio** Ladybug (2024.2+) or newer with Android SDK 36
- **JDK 11+**
- **Android device or emulator** running API 33 (Android 13) or higher
- *(Optional)* Google Cloud OAuth credentials for Drive sync

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
│       ├── AppCommon.kt          # Shared types (Screen, ChartPoint) + CSV helpers
│       ├── data/                 # DataRepository, RecurringExpenseEngine
│       ├── model/                # Expense, RecurringExpense
│       ├── sync/                 # SyncService, Google Drive
│       └── ui/modern/            # Aurora (Material 3) UI
│           ├── ModernMainActivity.kt  # Launcher: navigation, sync, CSV
│           ├── components/       # Cards, fields, dropdowns, calendar, charts
│           ├── screens/          # Dashboard, ExpenseList, Budget, Settings, ExpenseEntry
│           └── theme/            # AuroraTheme (colors, typography, shapes)
├── gradle/                       # Version catalog (libs.versions.toml)
├── tools/                        # build-release.ps1
└── .github/workflows/            # CI build workflow
```

## Sync configuration

### Signing (debug/CI builds)

The debug build type is pinned to the `debug.keystore` checked into the repo
root (`app/build.gradle.kts`), so every debug build — from `pr-build.yml`,
`assembleDebug` locally, or `assembleRelease` when no Play upload secret is
configured — always shares the same fixed signing certificate:

```
SHA-1: 5C:2B:BB:EA:6B:CD:69:AA:B8:31:C7:12:39:80:AD:42:3A:22:55:6F
```

This means installing a newer debug/CI-built APK over an older one never
fails with "App not installed" (signature conflict), and Google Sign-In
(below) only needs to be registered once.

### Google Drive

Google Sign-In here uses Android's package-name + SHA-1 matching — there's no
client ID or secret embedded in the app (the placeholder values in
`app/src/main/res/values/google-services.xml` aren't read by any code; ignore
that file). To make Drive sync work for a given signing key:

1. In [Google Cloud Console](https://console.cloud.google.com), open the
   project that has the **Google Drive API** enabled for this app (or enable
   it in a project of your choice).
2. **APIs & Services → Credentials → Create Credentials → OAuth client ID**,
   application type **Android**.
3. Package name: `com.example.expensetracker`.
4. SHA-1 certificate fingerprint: the debug fingerprint above (for
   `pr-build.yml`/local debug builds), or the fingerprint of your Play upload
   keystore if you're distributing a Play-signed release.
5. Save. No further wiring is needed in code — Play Services resolves the
   OAuth client automatically from the installed APK's package + signature
   at sign-in time.
6. If the OAuth consent screen is in **Testing** mode, add your Google
   account as a test user, or publish it if you'd rather not re-consent
   periodically.

Once that SHA-1 is registered, it stays valid for every future debug/CI
build — no need to repeat this unless you switch to a different signing key.

## License

Private project — all rights reserved.
