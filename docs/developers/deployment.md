# Deploying the web app

The web app runs like AgentTools: on a Plesk server with PHP 8.5 and PostgreSQL, deployed from
the Git repository with [`web/bin/deploy`](../../web/bin/deploy). The planned address is
`https://kgpsdiary.elkuku.dev`.

## The server

| What | Setting |
|---|---|
| Domain | e.g. `kgpsdiary.elkuku.dev`, HTTPS (Let's Encrypt) |
| Document root | `web/public` |
| PHP | 8.5 with `ctype`, `iconv`, `intl`, `gd` (JPEG, PNG, WebP, AVIF), `pdo_pgsql` |
| PHP limits | `upload_max_filesize = 20M`, `post_max_size = 25M` (photos up to 20 MB) |
| Database | PostgreSQL 15 or newer, an own database and user |
| Git | the repository, branch `main`; after each pull: `web/bin/deploy` |

`web/bin/deploy` (paths for Plesk's PHP 8.5 and Composer) removes the prod cache, installs the
dependencies without dev packages, writes `.env.local.php`, compiles the assets and runs the
migrations. It can be called from any folder.

## Configuration

Create `web/.env.local` on the server (it is not in Git) before the first deploy:

```dotenv
APP_ENV=prod
APP_SECRET=<32 random hex characters, e.g. from: php -r 'echo bin2hex(random_bytes(16));'>
DATABASE_URL="postgresql://<user>:<password>@127.0.0.1:5432/<database>?serverVersion=15&charset=utf8"
DEFAULT_URI=https://kgpsdiary.elkuku.dev

# The Web OAuth client of the KGPSDiary Google Cloud project, see google-sign-in.md
OAUTH_GOOGLE_ID=1234567890-abc.apps.googleusercontent.com

# A Mapbox public token for the web map; restrict it to https://kgpsdiary.elkuku.dev
MAPBOX_TOKEN=pk.…
```

`APP_FAKE_GOOGLE_TOKENS` must not be set: fake tokens only work in the dev environment anyway.

In the Google Cloud console, add `https://kgpsdiary.elkuku.dev` to the Web client's
**Authorized JavaScript origins**, so the "Sign in with Google" button works there.

## Photos

The photos are files in `web/var/photos`, next to the database. A deploy leaves them alone, but
they belong in the backup together with the database: a database without its photo files shows
broken images, and photo files without the database are orphans.

## The first admin

The first Google sign-in creates a user with `ROLE_USER`. Make yourself an admin on the server:

```shell
cd web
php bin/console user-admin
```
