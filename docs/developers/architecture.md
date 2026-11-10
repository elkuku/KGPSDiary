# Architecture

KGPSDiary is a monorepo with two applications that share one API and one way of signing in:

```
Android app ──HTTPS + Google ID token──▶ Symfony web app (API Platform) ──▶ PostgreSQL
  (app/)                                     (web/)                           + photo storage
Browser ──Sign in with Google──────────▶ Symfony web app (Twig + Mapbox GL JS)
```

## Android app (`app/`)

Kotlin and Jetpack Compose, package `org.elkuku.kgpsdiary`, minSdk 26.

Configuration comes from `app/local.properties` (see `local.properties.example`) and is compiled
into `BuildConfig` and resources:

| Property | Used as |
|---|---|
| `google.serverClientId` | `BuildConfig.GOOGLE_SERVER_CLIENT_ID`, the Web OAuth client ID |
| `server.baseUrl.debug`, `server.baseUrl.release` | `BuildConfig.SERVER_BASE_URL` per build type |
| `mapbox.accessToken` | string resource `mapbox_access_token`, read by the Mapbox SDK |
| `release.*` | release signing |

Languages: English, German, Spanish (`res/values{,-de,-es}`).

## Web app (`web/`)

Based on [elkuku/symfony-playground-two](https://github.com/elkuku/symfony-playground-two):
Symfony 8, Doctrine ORM on PostgreSQL, Twig with Bootstrap and Stimulus (Asset Mapper, no Node
build). API Platform and the Google token authenticator for the app are added in M1/M2.

## Authentication

Both sides use Google ID tokens issued for the **Web** OAuth client of the KGPSDiary Google Cloud
project, see [Google Sign-In setup](google-sign-in.md).
