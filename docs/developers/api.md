# API

The web app's API is built with API Platform and speaks JSON-LD under `/api`. The interactive
docs are at `/api/docs`.

## Authentication

Every request needs a Google ID token issued for the **Web** OAuth client, see
[Google Sign-In setup](google-sign-in.md):

```http
Authorization: Bearer <Google ID token>
```

The server verifies the token against `OAUTH_GOOGLE_ID` and requires a verified email address.
It finds the user by Google ID, else by email, else registers a new user with `ROLE_USER`.
Errors are `application/problem+json` with a `detail`, e.g. the `401` of a missing or invalid
token.

In dev, with `APP_FAKE_GOOGLE_TOKENS=1` in `web/.env.local`, fake tokens of the form
`fake|<googleId>|<email>|verified` are accepted too.

## Endpoints

| Method | Path | What |
|---|---|---|
| `GET` | `/api/me` | The signed-in user: `id`, `identifier` (the email address), `role` (`ROLE_USER` or `ROLE_ADMIN`). |

```shell
curl -H "Authorization: Bearer $ID_TOKEN" -H "Accept: application/ld+json" https://kgpsdiary.elkuku.dev/api/me
```
