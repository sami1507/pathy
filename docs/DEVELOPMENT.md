# Development guide

## Requirements

- JDK 17 or newer, Android SDK (compileSdk 35), Android Studio (recent) or just the command line
- A Firebase project (Auth, Firestore, Storage, Realtime Database, Functions) and a Mapbox account

## Only want to read or test the logic?

```bash
./gradlew :core:test
```

No accounts or secrets are needed. The domain logic lives in `core/` and has no Android dependency.

## Building the app

**1. Firebase client config.** Copy `google-services.json.example` to `app/google-services.json` and fill in your project's values from the Firebase console. The real file is git-ignored.

**2. Tokens in `local.properties`** (git-ignored):

```properties
sdk.dir=...
MAPBOX_DOWNLOADS_TOKEN=sk....   # secret token with DOWNLOADS:READ, used only to download the Mapbox SDK
MAPBOX_PUBLIC_TOKEN=pk....      # public token compiled into the app for the map (must start with pk.)
```

A secret `sk.` token is never accepted as the runtime map token. Without a public token the app still starts, with blank maps and a warning; a release build refuses to build.

**3. Server secrets** live in Firebase Secret Manager, never in the app:

```bash
firebase functions:secrets:set OPENAI_API_KEY
firebase functions:secrets:set PERSPECTIVE_API_KEY
firebase functions:secrets:set OPENWEATHER_API_KEY
# optional: the Scout model, see OPENAI_MODEL in functions/index.js
```

**4. Deploy the backend** (Cloud Functions need the Blaze plan because they call external APIs):

```bash
cd functions && npm install && cd ..
firebase deploy --only functions,firestore,storage,database
```

**5. Build and check:**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Automation

| What | Where | When |
|---|---|---|
| Credential scan | `scripts/check-secrets.sh` | Every push and pull request (CI job `secrets`), and before every local commit once you run `scripts/install-hooks.sh` |
| Core tests | `./gradlew :core:test` | Every push and pull request (CI job `core`), no secrets needed |
| Cloud Functions check | `npm ci` and `node --check` in `functions/` | Every push and pull request (CI job `functions`) |
| App tests, lint, build | `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` | CI job `app`, enabled by the repository variable `ANDROID_BUILD=true` plus the secrets `MAPBOX_DOWNLOADS_TOKEN`, `MAPBOX_PUBLIC_TOKEN`, `GOOGLE_SERVICES_JSON` |
| Dependency updates | `.github/dependabot.yml` | Weekly (Gradle), monthly (npm, actions); at most 3 open pull requests |

After cloning, run `scripts/install-hooks.sh` once so a credential can never be committed by accident.

## Languages

English, Arabic and Hebrew. Add or change text in `app/src/main/res/values*/strings.xml`; a test fails if a key or placeholder is missing in one language. See [LOCALIZATION.md](LOCALIZATION.md).

## Tips

- Build output goes to `%LOCALAPPDATA%\pathy-build` (or `~/pathy-build`), not the project folder, so synced folders such as OneDrive do not upload build files.
- If an emulator and the Gradle/Kotlin daemons run together on a machine with little free memory or disk, the Kotlin daemon can fail to start. Stop the emulator while building.
