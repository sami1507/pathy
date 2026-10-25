# Languages, fonts and translation

PATHY ships in **English, Arabic (العربية) and Hebrew (עברית)**. This page explains how that works and
the rules to follow when adding or changing text.

## How the app picks its language

* The user's choice is `system`, `en`, `ar` or `he`, stored by `i18n/AppLocale.kt`.
* **`system` (the default) follows the device**: the first device language PATHY has (Arabic, Hebrew or
  English), otherwise English. It never silently falls back to Hebrew.
* Every activity extends `i18n/PathyActivity`, which applies the language in `attachBaseContext`. This is
  what makes the setting work on a plain `ComponentActivity` below Android 13. Services that show text
  (for example `WalkieTalkieService`) wrap their context the same way.
* Code that has no activity (view models, repositories, SMS text) must resolve strings at call time with
  `AppLocale.wrap(context).getString(...)`. Do **not** keep `applicationContext.getString(...)`: the
  application context keeps the language it was created with until the process restarts.
* On Android 13+ the choice is mirrored to the system per-app language setting
  (`res/xml/locales_config.xml`), and a change made there is picked up again.
* Arabic uses **Western digits (0-9)**, the habit in the Levant and the same digits as the Latin units
  beside them. This comes from `ar-u-nu-latn` in `AppLocale.localeFor`.
* The App Bundle is configured not to split by language (`bundle.language.enableSplit = false`), because
  the language can change inside the app.

## Fonts

One family covers all three scripts: **Rubik** (SIL OFL 1.1), a single variable font in
`res/font/rubik.ttf`, licence in `assets/licenses/Rubik-OFL.txt`. Keeping one family means a Latin trail
name inside an Arabic or Hebrew sentence keeps the same weight and height instead of switching to a
fallback font.

`ui/theme/Type.kt` builds the type scale per script (`pathyTypography(language)`):

| Script | Letter spacing | Line height | Heaviest weight |
|---|---|---|---|
| Latin | as designed | 1.00 | ExtraBold |
| Hebrew | 0 | 1.06 | Bold |
| Arabic | **0** (tracking breaks the cursive joins) | 1.18 | Bold |

## Writing text in code

1. **No literal user-visible text in Kotlin.** Use `stringResource(R.string.x)` in Compose and
   `context.getString(R.string.x)` elsewhere. Add the key to all three files:
   `values/` (English), `values-ar/`, `values-iw/` (Hebrew uses the legacy `iw` folder name).
2. **Plurals** use `<plurals>` and `pluralStringResource` / `getQuantityString`. Arabic needs
   `zero one two few many other`; Hebrew needs `one two other`. Never write `"$n items"`.
3. **Placeholders are positional** (`%1$s`, `%2$s`) so a translation can reorder them. Pass text, not
   numbers, to `%s`.
4. **User-written text inside a sentence** (names, trail titles) goes through `String.isolateBidi()` so a
   Latin name does not reorder an Arabic or Hebrew sentence.
5. **Units** use the shared `unit_km`, `unit_m`, `unit_kmh`, `duration_*` strings.
6. **Times** use `i18n/RelativeTime` ("5 minutes ago") and
   `DateFormat.getBestDateTimePattern(locale, skeleton)` for dates, never a fixed pattern.
7. **Direction-sensitive icons** use `Icons.AutoMirrored.Filled.*` (back, forward, send, chevron).
8. **Stored values are not translated.** Some saved data is Hebrew or English on purpose (report
   categories, trail tags such as "הליכה", activity types, gender stored as `male`/`female`). Show it
   through a label function (`reportCategoryLabel`, `tagLabel`, `activityTypeLabel`, `genderLabel`) and
   leave the stored value alone, or old data stops matching.
9. A resource string must not start or end with a space (Android trims it). Put the space in code.

## Glossary (keep wording consistent)

| English | العربية | עברית |
|---|---|---|
| trail | مسار | מסלול |
| activity | نشاط | פעילות |
| record | تسجيل | הקלטה |
| bookmarks | المفضلة | מועדפים |
| group | مجموعة | קבוצה |
| event | فعالية | אירוע |
| community | المجتمع | קהילה |
| offline | دون اتصال | אופליין |
| Easy / Moderate / Hard / Extreme | سهل / متوسط / صعب / شديد الصعوبة | קל / בינוני / קשה / אתגרי מאוד |

Style: Arabic is clear Modern Standard with a generic "you"; Hebrew uses plural or neutral forms
(בחרו, נסו שוב) and noun forms for labels, to avoid gendered imperatives. Brand names (PATHY, Scout,
Google Maps) stay in Latin letters.

## Other places that follow the language

* **Scout (AI)**: the app sends `lang` to the `scoutChat` function, which tells the model to answer in
  that language. Suggestion chips and error messages are localised on the device.
* **Speech and voice guidance**: `AppLocale.speechLocale` feeds the speech recogniser and text-to-speech
  (it used to be fixed to `en-US` and Hebrew). The device must have that voice installed.
* **Map labels**: `Style.applyAppLanguage` asks the Mapbox Standard style for names in the app language.
  Hebrew name data is limited, so those fall back to the local name.
* **SMS and share text** (SOS message, trail share) are built in the sender's language.

## Checks

* `StringResourcesTest` fails if a key is missing in a language, placeholders differ between languages,
  a plural lacks a form a language needs, or an Arabic/Hebrew text is still English.
* `AppLocaleTest` covers language selection and Western digits.
* `TypographyScreenshotTest` and `LocalizedScreenshotTest` render screens in Arabic and Hebrew on the
  JVM (PNG output goes to the folder given by `pathy.shots.dir`, default the system temp folder).
* `gradlew :app:lintDebug` has translation checks of its own (missing translation, format arguments,
  plural forms, locale config).

## Known limits

* Some saved fallback names are still Hebrew (for example a group created without a name, a user with no
  display name in older posts). They are stored data, so they are not re-translated on screen.
* Trail names, descriptions and OpenStreetMap data come from outside the app and are shown as received.
* `navigation/EnhancedNavigationHelper.kt` is not used anywhere and its text is not localised.
