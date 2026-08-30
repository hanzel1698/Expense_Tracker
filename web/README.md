# Expense Tracker — web app

A browser port of the Android app, matching its Aurora (Material 3) UI, screens
and features. No build step, no dependencies: plain ES modules, CSS and one
HTML file, deployed to Netlify as static files.

## Run locally

```bash
cd web
python3 -m http.server 8000
```

Then open <http://localhost:8000>. Any static server works (`npx serve`,
`php -S`, …) — the app must be served over HTTP rather than opened as a
`file://` URL, because it uses ES modules.

## Deploy to Netlify

`netlify.toml` in the repo root already points Netlify at this directory:

```toml
[build]
  publish = "web"
  command = ""
```

Either way of connecting works:

- **Git-based** — in Netlify, *Add new site → Import an existing project*,
  pick this repository, and accept the settings from `netlify.toml`
  (build command empty, publish directory `web`). Every push to the connected
  branch redeploys.
- **CLI** — `npx netlify-cli deploy --prod` from the repo root.
- **Drag and drop** — drop the `web/` folder onto the Netlify dashboard.

There is nothing to install or compile, so builds take seconds.

## Feature parity with the Android app

| Android | Web |
| --- | --- |
| Dashboard: calendar, budget/balance/spent, trends, category breakdown | ✅ same layout, same drill-through dialogs |
| Expense list: search, date/attribute filters, by-store & by-item, detail views | ✅ |
| Budget: category & subcategory budgets, allocated/unallocated | ✅ |
| Add/edit expense: GST auto-calc, splits, calculator, store & location suggestions, drafts | ✅ |
| Settings: taxonomy management, recurring expenses, dev mode, sample data | ✅ |
| Recurring expense engine | ✅ same due-date maths (`js/recurring.js`) |
| CSV import / template export | ✅ same parser and template |
| Aurora light & dark themes | ✅ same palette, type scale and shapes |
| Local persistence | `expense_data.json` in app storage → the same JSON in `localStorage` |
| Google Drive backup | ✅ same folder and file naming, see below |
| Back button | Browser back maps to the same navigation rules |

The app is also installable (PWA) and works offline; all data lives in the
browser, so nothing leaves the device unless Drive sync is switched on.

## Data compatibility

The JSON written by the web app is byte-for-byte the same shape as the Android
app's `expense_data.json`, so a backup from either side restores into the other:

- **Android → web**: download the backup from Drive, then
  *Settings → Backup file → Restore…*
- **Web → Android**: *Settings → Backup file → Save backup*, put the file on the
  device, then *Restore from a backup file* in the Android app.

Restores **merge** rather than overwrite, using the same `MERGE_BY_DATE`
strategy as `SyncService.kt`.

## Google Drive sync (optional)

The Android app resolves its OAuth client from the APK's package name + SHA-1
signature. A web page can't do that, so the web app needs its own **Web
application** OAuth client ID:

1. In [Google Cloud Console](https://console.cloud.google.com), open the project
   that has the **Google Drive API** enabled (or enable it in a new one).
2. **APIs & Services → Credentials → Create Credentials → OAuth client ID**,
   application type **Web application**.
3. Under **Authorised JavaScript origins**, add the deployed origin
   (e.g. `https://your-site.netlify.app`) and, for local development,
   `http://localhost:8000`. No redirect URI is needed — the app uses the
   Google Identity Services token flow.
4. If the OAuth consent screen is in **Testing** mode, add your Google account
   as a test user.
5. Put the client ID in one of two places:
   - `web/config.js` — ships with the deploy, so every visitor gets it; or
   - *Settings → Google OAuth client ID* — stored in that browser only.

Everything except Drive sync works with no client ID configured.

Two things differ from Android by necessity:

- The scope is `drive.file`, so the web app only sees files **it** created. It
  will not list backups uploaded by the Android app (a different OAuth client).
  Move backups between the two with the file restore path above.
- Google's token flow needs a real origin, so Drive sync does not work from a
  `file://` page.

## Layout

```
web/
├── index.html               # App shell
├── config.js                # Optional Google OAuth client ID
├── manifest.webmanifest     # PWA manifest
├── sw.js                    # Service worker (network-first, offline fallback)
├── css/aurora.css           # Aurora palette, type scale, shapes, components
└── js/
    ├── app.js               # ModernMainActivity: navigation, sync, CSV, autosave
    ├── nav.js               # Screen / TrendDimension enums
    ├── model.js             # Expense, RecurringExpense, AppData + lenient parsing
    ├── store.js             # DataRepository: localStorage load/save
    ├── recurring.js         # RecurringExpenseEngine
    ├── csv.js               # CSV helpers, template, import pipeline
    ├── sync.js              # SyncService + Drive REST (Google Identity Services)
    ├── sample.js            # SampleDataManager
    ├── components.js        # AuroraComponents: fields, dropdowns, dialogs, pickers
    ├── calendar.js          # AuroraCalendar
    ├── charts.js            # AuroraCharts (canvas)
    ├── dom.js               # DOM builder + Material icon paths
    ├── util.js              # LocalDate/YearMonth maths, formatting
    └── screens/             # Dashboard, ExpenseList, Budget, Settings, Entry, SignIn
```

Each module names the Kotlin file it ports at the top.
