# LLConnect

A Material Design 3 Android client for a self-hosted [LubeLogger](https://github.com/hargata/lubelog)
instance. API calls are modeled on the LubeLogger v1.7.3 OpenAPI schema.

## AI Transparency

This app was essentially completely built using Claude Code. I've tried (and failed) to teach myself
Android Development and I didn't get very far, so this was my alternative option to create something
that would give me easy access to my LubeLogger instance.

## Getting started

1. Build & install the app (Android Studio, or `./gradlew installDebug`).
2. Open **Server** from the navigation drawer and enter:
   - **Scheme** (HTTP/HTTPS), **Host / IP**, and optional **Port**
   - **API key** (sent as `x-api-key`) — generate one in LubeLogger under
     *Settings → API Access* — or switch to **Username / password** (HTTP Basic).
3. Tap **Save & test connection** to persist the details and verify them against `/api/whoami`;
   the signed-in identity and server version appear below.

App preferences (theme, fuel-economy units, record-list order) live separately under **Settings**
and save the moment you change them. The theme offers System / Light / Dark / Midnight, where
Midnight is a dark theme with pure-black surfaces for OLED screens.

Self-hosted instances are often reached over plain HTTP on a LAN address, so cleartext
traffic is enabled in the manifest.

## Architecture

- **UI:** Jetpack Compose + Material 3 (dynamic color on Android 12+), Navigation Compose,
  a navigation drawer, and a `ViewModel` + `StateFlow` per screen.
- **Networking:** Retrofit + OkHttp + Moshi. The base URL is built dynamically from the
  saved connection settings; an `AuthInterceptor` injects auth and the `culture-invariant`
  header on every request. Custom Moshi adapters (`FlexDouble`/`FlexLong`/`FlexString`)
  tolerate both locale-mode (stringy) and invariant-mode (typed) responses.
- **Storage:** DataStore holds the connection config. Secrets (API key, Basic password) are
  encrypted with an AES-256/GCM key in the Android Keystore (`KeystoreCrypto`) — only
  ciphertext is written to disk. Legacy plaintext values are migrated on the next save.
- **DI:** a small manual `Graph` container initialized from `LLConnectApp`.

### Offline caching

GET responses are stored in an on-disk OkHttp cache so the app stays usable when the server
can't be reached. When online it always re-fetches fresh data; when the server is
unreachable it serves the cached copy instead.

- **Reachability** is tracked as `CHECKING → REACHABLE / UNREACHABLE`. A `ConnectivityManager`
  callback (`NetworkMonitor`) reacts to network changes instantly, and a lightweight
  background probe (`/api/whoami`) resolves the state without ever blocking the UI. While the
  server isn't confirmed reachable, GETs short-circuit straight to cache, so launching and
  navigating offline are instant. When a probe reconnects, screens auto-refresh to live data.
- **App-wide banner:** a neutral "Connecting…" bar while checking, and a red "Server
  unreachable — showing cached data" bar once it's confirmed down.
- **Settings → Offline cache:** shows current usage vs. the limit, a selectable maximum size
  (25 MB–500 MB), and a **Clear cache** button.

## Feature areas

| Area | Read | Add | Edit | Delete |
|------|:----:|:---:|:----:|:------:|
| Vehicles | ✅ | ✅ | ✅ | ✅ |
| Service / Repair / Upgrade | ✅ | ✅ | ✅ | ✅ |
| Fuel (Gas) | ✅ | ✅ | ✅ | ✅ |
| Odometer | ✅ | ✅ | ✅ | ✅ |
| Taxes | ✅ | ✅ | ✅ | ✅ |
| Planner / Supplies / Reminders / Equipment / Notes | ✅ | ✅ | ✅ | ✅ |
| Reminders / History / Reports (garage-wide) | ✅ | — | — | — |
| Server info / whoami / version · Tools (backup / cleanup / temp files / send reminders) | ✅ | — | — | — |

Add/edit forms are unified in `RecordFormScreen`, which shows only the fields each area
supports. Enum-backed fields (planner type/priority/progress, reminder metric) use
dropdowns whose values match the OpenAPI schema; the reminder form reveals the due-date
picker and/or due-odometer field to match the selected metric.

Records that carry attachments (everything except Reminders) have a full attachment
manager — **view/open, upload, rename, and delete** — reachable from the paperclip icon
on each record. Uploads use `/api/documents/upload`; view downloads the file through the
authenticated client and opens it with an external viewer via a `FileProvider`.

### Vehicle dashboard

Opening a vehicle shows a dashboard with a hero-image header (loaded through the
authenticated image client, with a text fallback when no photo is set) and the record
areas grouped into **Records** and **Planning & reference**. Each area tile displays a
live record count and the most-recent activity date — the soonest due date for
Reminders; count-only for Equipment and Notes, which have no date field. Summaries are
fetched in parallel and fill in lazily, so the screen paints immediately.

Vehicle and record lists support pull-to-refresh (which also clears the image cache), and
screens reload automatically when you return to them — e.g. after adding, editing, or
deleting a record, or changing the connection. Dated record lists (odometer, service, gas,
…) are ordered by date in the direction chosen in **Settings → Record list order**
(oldest-first by default); undated rows fall to the bottom.

### Favorite & quick entry

Tap the star on any vehicle (in the Vehicles list or on the home dashboard) to mark it your
favorite; a single vehicle is favorited at a time, so starring another moves the star. The
choice is stored on the device. When a favorite is set, the home dashboard shows two
quick-entry buttons — **Odometer** and **Fuel** — that jump straight to a new record for that
vehicle, skipping the vehicle → area navigation.

### Navigation drawer

The drawer holds the primary destinations (Dashboard, Vehicles, and the garage-wide views
below) at the top, with Tools, Server, About, and Settings pinned to the bottom. **Server**
holds the connection, authentication, and culture-invariant settings plus the live
signed-in/version info; **Settings** holds app preferences (theme, fuel-economy units, record-list
order). Swipe from the left edge to open the drawer; on the dashboard, a second back press
within two seconds exits the app (a first press closes the drawer if it's open).

### Garage-wide views

Several drawer screens aggregate across **all** vehicles using LubeLogger's `*/all`
endpoints, resolving each row's vehicle name from a single vehicle-list fetch:

- **Reminders** — every reminder across all vehicles, soonest due first, with the vehicle,
  due date/odometer, and urgency (`/api/vehicle/reminders/all`).
- **History** — a unified activity feed (service, repair, upgrade, tax, fuel, odometer)
  fetched in parallel and sorted newest first, each row showing its vehicle and cost.
- **Reports** — total spend with breakdowns by category and by vehicle, shown as
  proportion bars, derived from the same activity data.
- **Tools** — server maintenance actions: create a backup, clean up temp/orphaned files
  (with a deep-clean option), list temp files, and send reminders to collaborators. The
  server enforces the required admin/root permissions.
- **About** — app version, the connected host, and a link to the LubeLogger project.

The server also reports the signed-in identity: under API-key auth LubeLogger returns the
**API key's name** (labeled *API Key Name*), while username/password auth shows the actual
**User**.

## Suggested next steps

- App-wide search across vehicles and records.
- Per-list sort picker (in addition to the global default in Settings).
- Tag filters on History/Reports (the `tags` param; date-range filters are already in).
- Extra-field editing across records and vehicles.
- Optional biometric gate (`setUserAuthenticationRequired`) on the Keystore secret key.
