# KGPSDiary – Project Plan

A GPS diary. You save waypoints with a title, a description, categories and tags, photos and a visit date. You see and create them on a Mapbox map, on Android and on the web. The data is stored in PostgreSQL on the server.

The setup follows `~/repos/AgentTools`: one repository holding two applications that share one API and one way of signing in.

```
KGPSDiary/
├── app/    Android app (Kotlin, Compose, Mapbox Maps SDK)
├── web/    Symfony web app + API (from github.com/elkuku/symfony-playground-two)
└── docs/   developer docs (architecture, API, Google Sign-In setup)
```

```
Android app ──HTTPS + Google ID token──▶ Symfony web app (API Platform) ──▶ PostgreSQL
  (app/)                                     (web/)                           + photo storage
Browser ──Sign in with Google──────────▶ Symfony web app (Twig + Mapbox GL JS)
```

## 1. Data model (server, Doctrine)

| Entity | Fields |
|---|---|
| `User` | from the template: identifier (email), googleId, role, plus avatar and name |
| `Waypoint` | `id`, `owner` → User, `title`, `description` (text), `latitude`, `longitude`, `visitedAt` (date, editable), `createdAt`, `updatedAt`, `category` → Category (nullable), `tags` ↔ Tag, `photos` → Photo |
| `Category` | `id`, `owner`, `name`, `color`, `icon`. Each waypoint has at most one category, and the category sets the marker's color and icon. |
| `Tag` | `id`, `owner`, `name`. Many-to-many with waypoints, used for filtering. |
| `Photo` | `id`, `waypoint`, `filename`, `mimeType`, `width`, `height`, `takenAt`, `position` (sort order). The files are kept in `var/uploads`, outside the web root, and served through a controller that checks access. |

Coordinates are plain `float` columns. PostGIS is only needed if we later want queries like "near me" or "in this area" done in SQL; "in this map area" can be a simple `BETWEEN` filter on latitude and longitude.

Every record belongs to its owner, and voters make sure users only see their own data, as in AgentTools.

## 2. Web (`web/`)

**Base:** a fresh clone of [elkuku/symfony-playground-two](https://github.com/elkuku/symfony-playground-two) with its git history removed, so local files like `.env.local`, `vendor/` and `var/` don't come along. It brings Symfony 8, PHP 8.5, PostgreSQL through docker compose, Bootstrap, Stimulus with Asset Mapper, a user CRUD, `bin/install`, `bin/start` and `make tests`.

**Changes to the template:**
- Keep only Google sign-in: `GoogleIdentityAuthenticator` for the button and the dev login form. Remove the GitHub and GitLab authenticators.
- Add API Platform (`api-platform/symfony` and `api-platform/doctrine-orm`).
- Add `GoogleApiTokenAuthenticator`, `GoogleIdTokenVerifier` and `GoogleUserResolver`, ported from AgentTools. Unknown Google accounts are registered on their first request.
- For local development, the fake token verifier from AgentTools (`APP_FAKE_GOOGLE_TOKENS=1`).

**API** (under `/api`, `Authorization: Bearer <Google ID token>`):

| Method | Path | What |
|---|---|---|
| `GET` | `/api/me` | The signed-in user |
| `GET` | `/api/waypoints?bbox=…&tag=…&category=…&updatedSince=…` | The user's waypoints, filtered |
| `POST` | `/api/waypoints` | Create a waypoint |
| `GET`, `PATCH`, `DELETE` | `/api/waypoints/{id}` | Read, change or delete one |
| `POST` | `/api/waypoints/{id}/photos` | Upload a photo (multipart) |
| `GET` | `/api/photos/{id}/file?size=thumb\|full` | The image, after checking access |
| `DELETE` | `/api/photos/{id}` | Delete a photo |
| `GET`, `POST`, `PATCH`, `DELETE` | `/api/categories`, `/api/tags` | Manage categories and tags |

**Pages:**
- A map page using Mapbox GL JS from the importmap. It shows markers in their category colors, and a click on the map adds a waypoint.
- A waypoint list with search and filters.
- An edit form with photo upload.
- Category and tag management.
- The admin user CRUD from the template.

**Photos:** uploaded through VichUploaderBundle or a small uploader of our own, with thumbnails made by LiipImagineBundle. Images are resized on upload, to about 2048 px on the longest side.

## 3. Android (`app/`)

| Area | Choice |
|---|---|
| Package | `org.elkuku.kgpsdiary` |
| Language and UI | Kotlin 2.4, Jetpack Compose, Material 3, Navigation Compose, minSdk 26, target SDK 36 |
| Map | Mapbox Maps SDK v11 with `extension-compose`: point annotations in category colors, clustering |
| Sign-in | Credential Manager with `googleid`, the **Web** client ID as `serverClientId`, silent token refresh and one retry on `401` (from AgentTools' `auth/GoogleAuth.kt`) |
| API | Ktor client with kotlinx.serialization (as in AgentTools) |
| DI | Hilt |
| Offline cache | Room as a read cache of the user's waypoints, so the map works without a network connection. Writes go to the API first. An offline queue for writes is a later milestone. |
| Photos | Android Photo Picker and camera capture (`TakePicture`). Images are compressed before upload and shown with Coil. |
| Location | Fused Location Provider, for "center on me" and "add a waypoint here" |
| Build types | `debug` (server `http://10.0.2.2:8000`, optional fake sign-in) and `release` |
| Config | `local.properties` and `local.properties.example`: `google.serverClientId`, `server.baseUrl.*`, `mapbox.accessToken`, signing settings |

**Screens:**
1. **Sign-in:** the Google button.
2. **Map (start screen):** markers in category colors with clusters, a filter chip row for categories and tags, and "center on me".
   - A long-press creates a waypoint at that point, and the FAB creates one at your current location.
   - Tapping a marker opens a bottom sheet with the title, the visit date, photo thumbnails, Edit and Delete.
3. **Waypoint list:** sorted by visit date, with search and filters. Tapping an entry flies the map to that waypoint.
4. **Edit waypoint:** title, description, visit date picker, category, tags (chips), photos (add and remove), and the position, which you can adjust by dragging the marker.
5. **Categories and tags:** manage them, including colors.
6. **Profile:** your account and sign out.

## 4. Google Sign-In

As in AgentTools' `docs/developers/google-sign-in.md`: one Google Cloud project with two OAuth clients.

- **Web client:** its ID is `OAUTH_GOOGLE_ID` in `web/.env.local` and `google.serverClientId` in `app/local.properties`. Its authorized JavaScript origins are `http://localhost:8000` and the production URL.
- **Android client:** package `org.elkuku.kgpsdiary` plus the SHA-1 of the debug key. Release builds need a second Android client with the release key's SHA-1.

The server verifies every token against the Web client ID and requires a verified email.

## 5. Mapbox

- A **public token** (`pk.…`) is used in both projects. Restrict it by URL for the web, and keep a separate token for the app.
  - Web: `MAPBOX_TOKEN` in `.env.local`, passed to Stimulus through a data attribute.
  - App: `mapbox.accessToken` in `local.properties`, turned into a generated string resource.
- The Mapbox Maven repository may also need a **secret downloads token** (`sk.…`, `DOWNLOADS:READ`) in `~/.gradle/gradle.properties`. Check the current install guide when scaffolding.

## 6. Milestones

| # | Milestone | Done when |
|---|---|---|
| M0 ✅ | Scaffold | Git repo with `app/`, `web/` and `docs/`. `web` is copied from the template and runs with `bin/start`. An empty `app` builds. There is CI for both (GitHub Actions). |
| M1 ✅ | Sign-in | Google sign-in works on the web page and in the app. `/api/me` answers with the Bearer token. |
| M2 ✅ | Waypoints API | Entities, migrations, API resources and voters. PHPUnit tests for the API and for access to other users' data. |
| M3 ✅ | Web map | The map page shows your waypoints and can create, edit and delete them. |
| M4 ✅ | App map | The Mapbox map in the app with markers from the API, long-press to create, a detail sheet and an edit screen. |
| M5 ✅ | Categories and tags | On both sides: marker colors and filters. |
| M6 | Photos | Upload, thumbnails, galleries, and deleting, on both sides. |
| M7 | Offline | Room cache, and later an offline queue for writes. |
| M8 | Polish | List and search, translations (en, de, es?), app icon, a release build, deployment of the web app, and docs. |

Later ideas: GPX/GeoJSON import and export, recorded tracks, sharing a waypoint by link, offline map regions.

## 7. Decisions

| Topic | Decision |
|---|---|
| Hosting | The same server and setup as AgentTools, e.g. `kgpsdiary.elkuku.dev`, with PostgreSQL on that server |
| Google Cloud | A new project, KGPSDiary, with its own consent screen and its Web and Android clients |
| Grouping | One category per waypoint (marker color and icon) plus any number of tags |
| Languages | English, German and Spanish on both sides (`res/values{,-de,-es}` and `translations/`) |
| Offline | A read cache first (Room). An offline queue for writes comes in M7. |
| Mapbox | You have the tokens and enter them yourself in `app/local.properties` and `web/.env.local`, both git-ignored |
