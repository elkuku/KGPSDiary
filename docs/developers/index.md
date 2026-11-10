# Developers

- [Architecture](architecture.md)
- [API](api.md)
- [Google Sign-In setup](google-sign-in.md)

## Getting started

### Web

Needs PHP 8.5, Composer, the Symfony CLI and Docker.

```shell
cd web
bin/install   # Docker (PostgreSQL), dependencies, migrations, fixtures
bin/start     # Docker + Symfony server on https://127.0.0.1:8000
make tests    # PHPUnit, PHPStan, Rector, PHP-CS-Fixer
```

Secrets go to `web/.env.local` (git-ignored): `OAUTH_GOOGLE_ID` for the Google sign-in, and
`MAPBOX_TOKEN` (a Mapbox public token, `pk.…`) for the map at `/map`. In dev, `/login` also has a
form to sign in as any user without Google, e.g. the fixture users `user` and `admin`.

### App

Needs the Android SDK and JDK 17 or newer (Android Studio's bundled JBR works).

```shell
cd app
cp local.properties.example local.properties   # then fill in
export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug builds talk to `http://10.0.2.2:8000`, the host machine as seen from the emulator.

### Signing in without Google (debug)

Real sign-in needs the Google Cloud setup ([Google Sign-In setup](google-sign-in.md)). To try the
app against a local server without it:

1. `web/.env.local`: `APP_FAKE_GOOGLE_TOKENS=1`, then start the server without TLS so the emulator
   can reach it over plain HTTP: `symfony server:start -d --no-tls`.
2. `app/local.properties`: `debug.fakeSignInEmail=you@example.com`, and rebuild.

The debug app then "signs in" with a fake token that only the dev server accepts.

### On a phone

A phone connected with USB debugging reaches the dev server through `adb reverse`:

```shell
adb reverse tcp:8000 tcp:8000
```

and `server.baseUrl.debug=http://localhost:8000` in `app/local.properties` (debug builds allow plain
HTTP to `localhost` and `10.0.2.2` only).

The map needs `mapbox.accessToken` (a public `pk.…` token) in `app/local.properties`. The Mapbox
Maven repository needs no secret download token.
