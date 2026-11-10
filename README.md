# KGPSDiary

[![Web CI](https://github.com/elkuku/KGPSDiary/actions/workflows/web.yml/badge.svg)](https://github.com/elkuku/KGPSDiary/actions/workflows/web.yml)
[![Android CI](https://github.com/elkuku/KGPSDiary/actions/workflows/android.yml/badge.svg)](https://github.com/elkuku/KGPSDiary/actions/workflows/android.yml)

A GPS diary: save the places you visit as waypoints, with a title, a description, the day of the
visit, a category, tags and photos, and see them on a map, in the Android app and on the web.

- **Map:** your waypoints in their category colors; tap or click to add one where you are looking.
- **Categories and tags:** a category colors the waypoint's marker, tags help you find it again;
  both filter the map.
- **Photos:** up to 50 per waypoint, scaled and stored without their location data.
- **Tracks:** record where you walk with the app, also with the screen off; see your tracks on
  both maps, rename them, export them as GPX.
- **Share a position:** the app sends a waypoint's coordinates as a `geo:` link, which any map
  app opens; the title and description stay private.
- **List and search:** all waypoints, newest visit first, searched by title, description,
  category and tags.
- **Your way:** light or dark, and a quieter map without shops and restaurants.
- **Offline:** the app keeps your diary on the phone and shows it without a connection.
- **Private:** you sign in with Google and only ever see your own diary.
- In English, German and Spanish.

## Get the app

Android 8.0 or newer: the APK of the [latest release](https://github.com/elkuku/KGPSDiary/releases/latest).

## Development

| Folder | What |
|---|---|
| [`app/`](app) | Android app: Kotlin, Jetpack Compose, Mapbox Maps SDK, Room |
| [`web/`](web) | Symfony 8 web app and API Platform API, PostgreSQL, Mapbox GL JS |
| [`docs/`](docs/developers/index.md) | Developer docs: getting started, architecture, API, Google Sign-In, deployment, releases |

See [PLAN.md](PLAN.md) for the project plan and its milestones.
