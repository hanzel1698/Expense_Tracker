# Expense Tracker

An expense tracker for **Android** (Kotlin + Jetpack Compose) and the **web**
(static PWA, deployed to Netlify). Track daily spending, manage budgets by
category, set recurring expenses, and back up data to Google Drive.

The UI is **Aurora** — a Material 3 design with a teal/emerald palette, rounded tonal cards, and full light/dark theming (`ui/modern/`). `ModernMainActivity` is the app's launcher activity.

The web app in [`web/`](web/) is a port of that same UI and feature set, with no
build step and no dependencies. Both apps read and write the same JSON, so a
backup from one restores into the other — see [`web/README.md`](web/README.md).

## Features

- **Dashboard** — calendar view, expense summaries, recent transactions, budget overview, and category/monthly/year trend charts
- **Expense management** — add, edit, delete, search, and filter expenses; group by store or line item
- **Budgets** — allocate budgets by category and subcategory with unallocated balance tracking
- **Recurring expenses** — define repeating charges with automatic generation
- **Categories, subcategories & labels** — fully customizable taxonomy in Settings
- **Google Drive sync** — backup and restore expense data to a Drive app folder
- **Aurora UI** — Material 3 design with light and dark themes

## Web app

The same app runs in the browser from [`web/`](web/) — plain ES modules and CSS,
no build step:

```bash
cd web && python3 -m http.server 8000   # then open http://localhost:8000
```

Deployment is via **Netlify**; `netlify.toml` at the repo root already sets
`publish = "web"` with an empty build command, so importing the repository in
Netlify (or running `npx netlify-cli deploy --prod`) needs no further setup.

Setup details, Drive sync configuration and the Android ↔ web data-compatibility
notes are in [`web/README.md`](web/README.md).

## Requirements (Android app)

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

### Automated: build → GitHub Release → phone

`.github/workflows/android-release.yml` is the one-click path from a commit to
the app on a device. Trigger it manually from **Actions → Android Release Build
→ Run workflow** (optionally naming the run), and it will:

1. Stamp the build with `CI_VERSION_CODE = <run number>`, so `versionCode` is
   the run number and `versionName` is `1.<run number>` — every build is a
   distinct, increasing version.
2. Regenerate `app/src/main/assets/release_notes.json` from the commits since
   the last `v*` tag, so the in-app **What's New** screen shows this build's
   changes.
3. Build the release APK (signed with the checked-in `debug.keystore`, see
   [Signing](#signing-debugci-builds) — the fingerprint never changes, so each
   build installs over the previous one).
4. Upload the APK to **Firebase App Distribution**, which notifies the testers
   in the Firebase App Tester app on their device.
5. Publish a **GitHub Release** tagged `v1.<run number>` with the APK attached.

Steps 1–3 and 5 need no configuration. Step 4 is skipped with a warning until
these are set under **Settings → Secrets and variables → Actions**:

| Name | Kind | Value |
| --- | --- | --- |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Secret | Contents of a Google Cloud service-account JSON key with the **Firebase App Distribution Admin** role |
| `FIREBASE_APP_ID` | Variable | The Firebase Android App ID, e.g. `1:123456789012:android:abcdef…` |
| `FIREBASE_TESTERS` | Variable *(optional)* | Comma-separated tester emails (defaults to `hanzel.h.fernandez@gmail.com`) |

To get those values:

1. In the [Firebase console](https://console.firebase.google.com), open (or
   create) a project and **Add app → Android** with package name
   `com.example.expensetracker`. No `google-services.json` or SDK is needed —
   App Distribution uploads the APK server-side.
2. Copy the **App ID** from **Project settings → General → Your apps** into the
   `FIREBASE_APP_ID` variable.
3. **Project settings → Service accounts → Manage service account permissions**
   (Google Cloud IAM) → create a service account, grant it **Firebase App
   Distribution Admin**, then create a JSON key and paste the whole file into
   the `FIREBASE_SERVICE_ACCOUNT_JSON` secret.
4. In **App Distribution → Testers & Groups**, add the tester emails, and
   install the **Firebase App Tester** app on the device from the invite email.

`pr-build.yml` stays the lightweight check — manual, debug + release APKs as
build artifacts, no release, no distribution.

### Local

Use the repo-relative build script:
```powershell
.\tools\build-release.ps1
```

This produces a release APK under `dist/`.

For production signing, set the `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` /
`KEY_ALIAS` / `KEY_PASSWORD` environment variables (or drop an
`upload-keystore.jks` in `~/.android/signing/`) — `app/build.gradle.kts` picks
that up automatically and signs the release with it instead of the debug key.

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
├── web/                          # Web app (static PWA, Netlify)
│   ├── index.html                # App shell
│   ├── css/aurora.css            # Aurora palette, type scale, components
│   └── js/                       # Ports of the Kotlin modules above
├── netlify.toml                  # Netlify config (publish = web, no build)
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
