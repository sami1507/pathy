# PATHY architecture

The app is organised so that the logic a hiker depends on (recording a walk, following a trail, grading
a trail) is plain Kotlin that can be tested on a laptop, and the Android parts are thin.

```
core/            Gradle module :core - pure Kotlin, no Android or Mapbox types  -> plain JVM unit tests
                 (core/src/main/kotlin/com/example/pathy/core)
  geo/           distance, bearing, polyline projection/simplification, polyline codec
  track/         GPS filter, elevation accumulator, recorder (pause, auto-pause, calories)
  nav/           RouteNavigator (turns, off-route, arrival), VoiceCueScheduler
  osm/           Overpass queries, parser (relations stitched into one line), classifier, TrailBuilder,
                 TrailAvailability (closed / private / seasonal / outdated), place-search parser
  trail/         grades, durations, elevation profile maths, hiker condition reports
  gpx/           GPX read/write

tracking/        recording on the phone: Room entities, RecordingService (foreground, type location),
                 RecordingRepository, RecordingHub (state for the screens), UploadWorker (WorkManager)
history/         cloud history (one Firestore document per outing) + local outings
data/osm/        Overpass client (mirrors, retry, disk cache), ElevationService, TrailRepository
offline/         offline map areas (Mapbox TileStore)
navigation/      NavGuidance: turns navigator state into words in the app language
export/          GPX sharing (FileProvider)
screens/, ui/    Compose screens and theme
i18n/            AppLocale (system / en / ar / he), fonts, bidi helpers
```

## Recording a walk

1. `TrackingActivity` (or the navigation screen) asks `RecordingController` to start.
2. `RecordingService` runs as a foreground service (type `location`, notification with Pause / Finish).
   It feeds each GPS fix to `TrackRecorderCore`, which rejects bad fixes (poor accuracy, jumps, out of
   order), ignores standing-still noise, smooths altitude (dead-band) and pauses itself when you stop.
3. Every accepted point is written to Room as it arrives. If Android kills the process the service is
   restarted (`START_STICKY`) and rebuilds the recorder from the database, including a pause that was
   in progress.
4. On finish the outing is marked FINISHED and `UploadWorker` is queued. It uploads when a network is
   available and the user is signed in, retries with back-off, and deletes uploaded copies after 30 days.
5. The cloud document stores the route as an encoded polyline plus two small arrays (altitude in
   decimetres, seconds from start): one write, one read. Outings with more than 12,000 points are
   simplified first. Older per-point documents are still readable.

## Following a trail

`RouteNavigator` follows the walker along the route itself. Left/right come from the shape of the line,
the walker can join anywhere within 40 m of it, going off route needs three fixes in a row, coming back
needs a clear return (20 m), and a loop only finishes after most of it was walked. `VoiceCueScheduler`
decides when to speak (about 150 m, 40 m, and at the turn) so instructions are not repeated.

## Trail data

`TrailRepository` makes one Overpass request per area (snapped to a ~500 m grid), caches it in memory
for 10 minutes and on disk for 6 hours, tries three servers and falls back to an older saved answer when
offline. Relations (marked routes) are stitched into one named line. Climb is never guessed: it is the
`ascent` tag when OSM has it, otherwise unknown until `ElevationService` (Open-Meteo terrain data)
measures it on the trail screen. Grades come from the SAC scale / Shenandoah effort / MTB scale /
track type; times from Naismith-style rules.

## Offline

`OfflineRegions` saves the style and tiles around a trail with Mapbox's offline storage. The map uses
them automatically with no connection. (Earlier code downloaded tiles from openstreetmap.org and served
them through a local web server; both are gone.)

## Walkie-talkie

Nearby Connections without internet, WebRTC with it, one coordinator and a foreground service to keep a call
alive with the screen off. See [WALKIE_TALKIE.md](WALKIE_TALKIE.md).

## Trail data

OpenStreetMap through Overpass, stitched, classified, assessed for availability and freshness, and combined with
hiker reports. See [TRAIL_DATA.md](TRAIL_DATA.md).

## Tests

| Command | What runs | Needs secrets |
|---|---|---|
| `./gradlew :core:test` | The domain logic on the plain JVM: GPS filter, recorder, route following, OSM parsing against real Overpass data, trail assessment, GPX, polyline codec | No |
| `./gradlew :app:testDebugUnitTest` | Room (in memory, including the 1 -> 2 migration), the cloud document format, navigation wording in all three languages, string parity (en/ar/he), locale handling, and screenshot tests in Arabic and Hebrew | Yes (to resolve the Mapbox SDK) |
| `./gradlew :app:lintDebug` | Android lint | Yes |

CI (`.github/workflows/ci.yml`) always runs `:core:test`; the app job runs when the repository has the Mapbox and
Firebase secrets configured.
