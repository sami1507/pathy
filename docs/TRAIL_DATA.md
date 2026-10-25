# Trail data

Where trails come from, how they are judged, and what that cannot tell you.

## Source

Trails come from [OpenStreetMap](https://www.openstreetmap.org/copyright) (© OpenStreetMap contributors, ODbL) through the Overpass API. Places are found with the OpenStreetMap place search (Nominatim). The app identifies itself, asks for no more than one place search a second, and does not search while the person is typing.

## From map data to a trail (`:core`)

1. **Query** (`OverpassQueries`): one request per area for the chosen kinds (hiking, mountain biking, cycling, off-road). The area is snapped to a ~500 m grid so repeated requests are cache hits; geometry is cut to the search square so a country-long route sends only the part nearby.
2. **Parse** (`OverpassParser`): route relations are stitched into one ordered line from their member ways, whatever the order and direction of those ways. Pieces that cannot be joined are kept as extra segments.
3. **Classify** (`OsmClassifier`): decides the kind from the tags. A named footway is usually a town pavement, so footways count only when the data says rough ground (`sac_scale`, `trail_visibility`).
4. **Assess availability** (`TrailAvailability`):
   - **Left out:** `access=no/private`, military zones, construction, proposed or abandoned routes, `disused:`/`abandoned:` lifecycle tags, a foot ban on a walking trail, and so on. A permission for the specific way of travel wins over a general restriction (closed to cars, open to walkers).
   - **Listed with a warning:** seasonal or conditional access, poor trail visibility, a pending `fixme`, and data nobody has verified or edited for six years.
   - **Freshness:** `check_date` / `survey:date` when someone verified the trail on the ground, otherwise the last edit time.
5. **Grade and time** (`TrailMetrics`): SAC scale, the Shenandoah effort formula, MTB scale or track type; walking time by Naismith's rule. An unknown climb stays unknown and never feeds the grade as an invented number.
6. **Rank** (`quality`, 0-10): marked and named routes in a walking network, with a recent check, come before bare paths.

All of this is plain Kotlin tested against real Overpass data from Haifa (`core/src/test/resources`).

## Elevation

The `ascent` tag when OpenStreetMap has it. Otherwise measured on demand from the Open-Meteo terrain model, sampled about every 50 m with a 4 m dead-band so noise does not add climb. If the service is unreachable the app shows "unavailable".

## Hiker reports

`trail_conditions/<trail>/reports/<user>` in Firestore: one report per person and trail (open, muddy, blocked, closed, not found), optional note. Only reports from the last 45 days count, one per account, and the newest decides the colour. See `TrailCondition` and `firestore.rules`.

## What it cannot promise

OpenStreetMap is edited by volunteers. A trail can be closed by a storm yesterday and look fine in the data, or be missing from the map. The app says where each piece of information comes from and how old it is, and always tells people to check local signs and conditions.
