# Releasing the app

A tag like `v1.0.0` builds a signed release APK on GitHub Actions
([`android-release.yml`](../../.github/workflows/android-release.yml)) and publishes it as a
GitHub release, with notes generated from the commits. The version name is the tag without the
`v`, the version code the workflow's run number.

```shell
git tag v1.0.0
git push origin v1.0.0
```

The workflow can also be started by hand (Actions → Android release → Run workflow); it then
uploads the APK as a workflow artifact instead of publishing a release.

## One-time setup

### The release key

Create a keystore outside the repository and keep it, and its password, safe: every update of the
app must be signed with the same key.

```shell
keytool -genkeypair -v -keystore ~/kgpsdiary-release.jks -alias kgpsdiary \
  -keyalg RSA -keysize 4096 -validity 10000
```

Use the same password for the keystore and the key.

### Google Sign-In

Release builds are signed with this key, not the debug key: add another **Android** OAuth client
with the package `org.elkuku.kgpsdiary` and the release key's SHA-1 (see
[Google Sign-In setup](google-sign-in.md)):

```shell
keytool -list -v -keystore ~/kgpsdiary-release.jks -alias kgpsdiary
```

### Repository secrets

Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 ~/kgpsdiary-release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore's password |
| `RELEASE_KEY_ALIAS` | `kgpsdiary` |
| `GOOGLE_SERVER_CLIENT_ID` | the **Web** client ID (as `OAUTH_GOOGLE_ID` on the server) |
| `MAPBOX_ACCESS_TOKEN` | a Mapbox public token (`pk.…`) for the app |

## A release build on your machine

With the `release.*` properties in `app/local.properties` (see `local.properties.example`):

```shell
cd app
./gradlew assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. Without the `release.*` properties
the build is unsigned and cannot be installed.
