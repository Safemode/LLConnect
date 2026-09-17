# LLConnect

A Material Design 3 Android client for a self-hosted [LubeLogger](https://github.com/hargata/lubelog)
instance. API calls are modeled on the LubeLogger v1.7.3 OpenAPI schema.

## Getting started

1. Build & install the app (Android Studio, or `./gradlew installDebug`).
2. Open **Settings** from the navigation drawer and enter:
   - **Scheme** (HTTP/HTTPS), **Host / IP**, and optional **Port**
   - **API key** (sent as `x-api-key`) — generate one in LubeLogger under
     *Settings → API Access* — or switch to **Username / password** (HTTP Basic).
3. Tap **Save & test** to verify the connection against `/api/whoami`.

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

## Feature areas

| Area | Read | Add | Edit | Delete |
|------|:----:|:---:|:----:|:------:|
| Vehicles | ✅ | ✅ | ✅ | ✅ |
| Service / Repair / Upgrade | ✅ | ✅ | ✅ | ✅ |
| Fuel (Gas) | ✅ | ✅ | ✅ | ✅ |
| Odometer | ✅ | ✅ | ✅ | ✅ |
| Taxes | ✅ | ✅ | ✅ | ✅ |
| Planner / Supplies / Reminders / Equipment / Notes | ✅ | ✅ | ✅ | ✅ |
| Server info / whoami / version / backup | ✅ | — | — | — |

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
screens reload automatically when connection settings change.

## Suggested next steps

- Extra-field editing across records and vehicles.
- Date/tag filtering on record lists (the `*/all`, `startDate`, `endDate`, `tags` params).
- Optional biometric gate (`setUserAuthenticationRequired`) on the Keystore secret key.
