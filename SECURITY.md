# Security

## Reporting a problem

Please open a private security advisory on GitHub ("Security" tab, "Report a vulnerability") instead of a public issue.

## How secrets are handled

| Secret | Where it lives | In the APK? |
|---|---|---|
| OpenAI, moderation and weather API keys | Firebase Secret Manager, read only by the Cloud Functions in `functions/` | **No** |
| Mapbox **public** token (`pk.`) | `local.properties` / CI secret, compiled into the app | Yes, by design: it is public and restricted by Mapbox |
| Mapbox **secret** download token (`sk.`) | `local.properties` / CI secret, used only to download the SDK at build time | **No** |
| Firebase client configuration (`google-services.json`) | Not in this repository; copy `google-services.json.example` | Yes (it identifies the project, access is controlled by the rules) |

The build enforces this: a secret `sk.` token is never accepted as the runtime map token, and a release build stops if no public token is set.

## Server-side rules

- **Firestore** (`firestore.rules`): users write only their own data; recorded activities and trail reports are validated for shape and size (no negative distances, no oversized routes); a user can keep one condition report per trail.
- **Storage** (`storage.rules`): uploads must be images under a size limit and carry the uploader's id; only that user can delete them.
- **Realtime Database** (`database.rules.json`): the walkie-talkie switchboard. A person writes only their own entry, and a connection-setup message must really come from the account it names. Only the recipient can read their messages.
- **Cloud Functions**: the AI, weather, moderation and report functions require a signed-in user; AI use is capped per user per day, and the sign-in lookup is rate limited.

## Known gaps

- Anyone signed in who knows a group id can join its walkie-talkie call (groups are open to join). Closing this needs group membership mirrored where the Realtime Database rules can check it.
- Live location sharing between group members is intentionally not shipped for the same reason.
- The rules have been reviewed but not yet run against the Firebase emulator suite (no automated rules tests in CI yet).

## History

The earlier private development history of this project was not published because it contained credentials. This repository starts from a clean import of the current code, and the code reads all secrets from configuration, never from source files.
