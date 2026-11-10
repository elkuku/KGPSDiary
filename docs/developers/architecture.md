# Architecture

KGPSDiary is a monorepo with two applications that share one API and one way of signing in:

```
Android app ──HTTPS + Google ID token──▶ Symfony web app (API Platform) ──▶ PostgreSQL
  (app/)                                     (web/)                           + photo storage
Browser ──Sign in with Google──────────▶ Symfony web app (Twig + Mapbox GL JS)
```

## Android app (`app/`)

Kotlin and Jetpack Compose, package `org.elkuku.kgpsdiary`, minSdk 26.

| Area | Where | What |
|---|---|---|
| Sign-in | `auth/GoogleAuth.kt` | Credential Manager "Sign in with Google" with the **Web** client ID as `serverClientId`. Debug builds can use `FakeGoogleAuth` (`src/debug`). |
| Session | `auth/SessionManager.kt` | The signed-in account; API calls with its token, refreshed silently and retried once on `401`. |
| API client | `data/KgpsApi.kt` | Ktor client for the JSON-LD API; answers become `ApiResult`s with the server's messages. |
| DI | `di/` | Hilt modules: the HTTP client and server URL, the Google sign-in per build type. |
| Diary | `data/DiaryRepository.kt` | The user's waypoints, categories and tags as last loaded; saving and deleting go to the server first. |
| Map | `ui/map/` | Mapbox Maps SDK (Compose extension): clustered markers in category colors, a long press adds a waypoint there, the + button one at the map's center, a tap shows a detail sheet. |
| Categories and tags | `ui/manage/` | Adding, renaming, recoloring (12 marker colors) and deleting categories and tags, with their waypoint counts. The map filters by one category and one tag (chips above the map). |
| Edit | `ui/edit/` | Creating and editing a waypoint: title, description, position, visit date, category, tags and photos (Android photo picker; picked photos are uploaded once the waypoint is saved). |
| Photos | `ui/photo/`, `data/PhotoScaler.kt`, `KGPSDiaryApplication.kt` | Thumbnails and a full screen viewer with Coil, whose image loader adds the bearer token for the server; photos are scaled to 2048 pixels on the phone before the upload. |
| UI | `ui/` | `MainViewModel` (session), `AppNavHost` (type-safe Navigation Compose), the sign-in and profile screens. |

Configuration comes from `app/local.properties` (see `local.properties.example`) and is compiled
into `BuildConfig` and resources:

| Property | Used as |
|---|---|
| `google.serverClientId` | `BuildConfig.GOOGLE_SERVER_CLIENT_ID`, the Web OAuth client ID |
| `server.baseUrl.debug`, `server.baseUrl.release` | `BuildConfig.SERVER_BASE_URL` per build type |
| `mapbox.accessToken` | string resource `mapbox_access_token`, read by the Mapbox SDK |
| `debug.fakeSignInEmail` | debug only: `BuildConfig.FAKE_SIGN_IN_EMAIL`, sign in with fake tokens |
| `release.*` | release signing |

Languages: English, German, Spanish (`res/values{,-de,-es}`).

## Web app (`web/`)

Based on [elkuku/symfony-playground-two](https://github.com/elkuku/symfony-playground-two):
Symfony 8, Doctrine ORM on PostgreSQL, Twig with Bootstrap and Stimulus (Asset Mapper, no Node
build), and API Platform for the [API](api.md).

| Area | Where | What |
|---|---|---|
| Entities | `src/Entity/` | `User`, and the diary: `Waypoint`, `Category`, `Tag`, each owned by one user. |
| Owner scoping | `src/Doctrine/`, `src/State/`, `src/Security/Voter/`, `src/Validator/` | Users only see and change their own records, admins too. |
| Photos | `src/Photo/`, `src/Controller/Api/`, `src/Controller/Photo/` | `PhotoStorage` (GD: upright, scaled, JPEG without metadata, in `var/photos`), `PhotoUploader`, files for the API and the web, deleting files with their photos. |
| Security | `src/Security/` | `GoogleIdentityAuthenticator` (web button, with Google's CSRF cookie), `GoogleApiTokenAuthenticator` (API bearer token), both through `GoogleIdTokenVerifier` and `GoogleUserResolver`. |
| API | `src/ApiResource/`, `src/State/` | The resources under `/api`. |
| Pages | `src/Controller/` | One class per action: login, profile, the user admin, and the diary in `Waypoint/`: the map, its GeoJSON, and creating, showing, editing and deleting waypoints. |
| Map | `assets/controllers/map_controller.js` | Stimulus controller with Mapbox GL JS (from the importmap): clustered waypoints in their category colors; a click on the map offers a new waypoint there; filters by category and tag. |
| Categories and tags | `src/Controller/Category/`, `src/Controller/Tag/` | Lists with waypoint counts, a form for a new one, editing and deleting (the waypoints stay). |
| Languages | `translations/` | English, German, Spanish (ICU messages), picked by the browser's `Accept-Language`. |

## Authentication

Both sides use Google ID tokens issued for the **Web** OAuth client of the KGPSDiary Google Cloud
project, see [Google Sign-In setup](google-sign-in.md).
