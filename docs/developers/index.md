# Developers

- [Architecture](architecture.md)
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

Secrets go to `web/.env.local` (git-ignored): `OAUTH_GOOGLE_ID`, and later `MAPBOX_TOKEN`.

### App

Needs the Android SDK and JDK 17 or newer (Android Studio's bundled JBR works).

```shell
cd app
cp local.properties.example local.properties   # then fill in
export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug builds talk to `http://10.0.2.2:8000`, the host machine as seen from the emulator.
