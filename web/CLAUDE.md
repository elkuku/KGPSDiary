# CLAUDE.md

@AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

The web app and API of KGPSDiary (see ../PLAN.md and ../docs/developers), built from the symfony-playground-two template. Users sign in with Google only: the "Sign in with Google" button on the web, a Google ID token as bearer token on the API (used by the Android app in ../app). In `dev` a form-based login is available too.

## Commands

### Setup
```bash
bin/install   # Full setup: Docker, migrations, fixtures
bin/start     # Start dev environment (Docker + Symfony server)
bin/stop      # Stop Docker containers
```

### Testing & Quality (run via `symfony php` / `symfony console` to pick up correct PHP)
```bash
make tests                              # Full test suite: PHPUnit + PHPStan + Rector (dry-run) + PHP-CS-Fixer
vendor/bin/phpunit --testdox tests/Path/To/SomeTest.php  # Single test file
vendor/bin/phpstan --memory-limit=256M  # Static analysis (level 8+)
vendor/bin/rector process --dry-run     # Check modernization suggestions
vendor/bin/php-cs-fixer check --diff    # Check code style
vendor/bin/php-cs-fixer fix             # Auto-fix code style
```

`make tests` drops and recreates the test database before running PHPUnit (uses `APP_ENV=test`).

### Maintenance
```bash
composer check       # Check outdated assets, PHP deps, and recipes
composer db-diagram  # Regenerate assets/docs/database.svg
symfony console user-admin  # Interactive user management CLI
```

## Architecture

### Entities & Enums
- **`User`** (`system_user` table) — single entity, implements `UserInterface`. Uses `identifier` (unique string) as the username. `googleId` is nullable until the user first signs in with Google. Role is stored via `UserRole` enum.
- **`UserRole`** enum — `ROLE_USER` (default) and `ROLE_ADMIN`.
- **`Waypoint`**, **`Category`**, **`Tag`** — the diary, all API Platform resources and `OwnedInterface` (`OwnedTrait`): each belongs to one user.

### Owner scoping
Users only see their own records, admins too: `Doctrine\OwnerExtension` restricts API queries (other users' records are 404, their IRIs 400), `State\OwnerProcessor` sets the owner of new records, `Security\Voter\OwnerVoter` grants `OWNER`. Validators: `UniqueForOwner` (names per user), `OwnedByUser` (references). Filters for waypoints are `QueryParameter`s (`#[ApiFilter]` is deprecated): `Filter\BboxFilter`, `Filter\UpdatedSinceFilter` and API Platform's `IriFilter`.

### Controllers
Single-action controller pattern under `src/Controller/`:
- `User/` — CRUD (Index, Create, Read, Update, Delete), each in its own class
- `Security/` — `GoogleIdentityController` (route for the Google button), `LoginFormController` and `Logout`
- `System/` — `AboutController`, `LogviewController`
- `Waypoint/` — the diary on the web: `Map` (`/map`), `Features` (`/map/waypoints.geojson`), `Create`, `Read`, `Update`, `Delete`; other users' waypoints are 404 (`IsGranted(OWNER, statusCode: 404)`)
- `ProfileController`, `DefaultController` (start page)

Access control uses `#[IsGranted()]` attributes.

### Security
- `main` firewall: `LoginFormAuthenticator` (dev/test only) and `GoogleIdentityAuthenticator` (posts from Google's button to `/connect/google/verify`, checks Google's `g_csrf_token` double-submit cookie). Remember-me is enabled.
- `api` firewall (`^/api/`, stateless): `GoogleApiTokenAuthenticator` reads `Authorization: Bearer <Google ID token>`; errors are `application/problem+json`.
- Both verify tokens through `GoogleIdTokenVerifierInterface` (`GoogleIdTokenVerifier`, audience `OAUTH_GOOGLE_ID`) and find or register the user with `GoogleUserResolver` (by Google ID, else by email, else new `ROLE_USER`; the email must be verified).
- Tests use `FakeGoogleIdTokenVerifier` (tokens from `FakeGoogleIdTokenVerifier::token()`); in dev `DevGoogleIdTokenVerifier` accepts them too when `APP_FAKE_GOOGLE_TOKENS=1`.

### API
API Platform under `/api` (docs at `/api/docs`). Resources in `src/ApiResource/`, providers/processors in `src/State/`. `GET /api/me` returns the signed-in user. The endpoints are documented in ../docs/developers/api.md.

### Frontend
Asset Mapper (no Webpack), Bootstrap, Stimulus, Twig Components, UX Icons. The map is `assets/controllers/map_controller.js` with Mapbox GL JS from the importmap; the token is the Twig global `mapbox_token` (`MAPBOX_TOKEN`). Texts are translated (`translations/messages+intl-icu.{en,de,es}.yaml`), the locale comes from `Accept-Language`.

### Database
PostgreSQL via Docker Compose. Doctrine ORM with migrations. Fixtures in `src/DataFixtures/` create one regular user and one admin.

### Custom Bundle
`KuKuSFSystemToolsBundle` — provides system utility endpoints (about, log viewer).

## Environment Variables
Secrets go in `.env.local` (gitignored):
- `OAUTH_GOOGLE_ID`: the Web OAuth client ID of the KGPSDiary Google Cloud project (the app's `google.serverClientId`)
- `MAPBOX_TOKEN`: Mapbox public token (`pk.…`) for the web map
- `APP_FAKE_GOOGLE_TOKENS=1`: dev only, accept fake ID tokens on the API (for the debug app's fake sign-in)
