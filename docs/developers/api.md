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
| `GET` | `/api/waypoints` | The user's waypoints, newest visit first, 30 per page (`itemsPerPage` up to 1000). Filters below. |
| `POST` | `/api/waypoints` | Create a waypoint (`201`; `422` with `violations` for invalid data). |
| `GET`, `PATCH`, `DELETE` | `/api/waypoints/{id}` | Read, change (`application/merge-patch+json`) or delete one. |
| `GET`, `POST` | `/api/categories` | The user's categories, sorted by name; create one. |
| `GET`, `PATCH`, `DELETE` | `/api/categories/{id}` | Read, change or delete one. Deleting removes it from its waypoints. |
| `GET`, `POST` | `/api/tags` | The user's tags, sorted by name; create one. |
| `GET`, `PATCH`, `DELETE` | `/api/tags/{id}` | Read, change or delete one. Deleting removes it from its waypoints. |

Users only ever see their own records, admins included: another user's waypoint, category or tag is
`404`, and its IRI in a request body is `400`.

## Waypoints

```json
{
  "title": "Mitad del Mundo",
  "description": "The equator.",
  "latitude": -0.0022,
  "longitude": -78.4558,
  "visitedAt": "2026-10-31",
  "category": "/api/categories/3",
  "tags": ["/api/tags/7", "/api/tags/9"]
}
```

| Field | |
|---|---|
| `title` | Required, up to 255 characters. |
| `description` | Free text, up to 20000 characters. |
| `latitude`, `longitude` | Required, WGS 84 degrees (-90 to 90, -180 to 180). |
| `visitedAt` | The day of the visit (`Y-m-d`) or `null`. |
| `category` | The IRI of one of the user's categories, or `null`. |
| `tags` | IRIs of the user's tags. |
| `createdAt`, `updatedAt` | Read only, set by the server. |

Answers embed the category (`id`, `name`, `color`, `icon`) and the tags (`id`, `name`).

### Filters

| Parameter | |
|---|---|
| `bbox=minLon,minLat,maxLon,maxLat` | Only waypoints in this area, e.g. the map's visible bounds. `minLon > maxLon` crosses the antimeridian. |
| `category=/api/categories/3` | Only waypoints of this category. |
| `tag=/api/tags/7` | Only waypoints with this tag. |
| `updatedSince=2026-11-10T12:00:00Z` | Only waypoints changed at or after this time, for syncing a cache. |

Invalid filter values are `400`.

## Categories and tags

A category has a `name` (required, unique per user, up to 50 characters), a `color` (`#rrggbb`,
default `#2e7d32`) and an `icon` (a [Material Symbols](https://fonts.google.com/icons) name like
`hiking`, or `null`). A tag only has a `name` (required, unique per user).

```shell
curl -H "Authorization: Bearer $ID_TOKEN" -H "Accept: application/ld+json" https://kgpsdiary.elkuku.dev/api/me
```
