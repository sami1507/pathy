/**
 * PATHY server-side functions.
 *
 * All third-party API keys (OpenAI, Perspective, OpenWeather) live here as Firebase secrets
 * and never ship inside the APK. Every callable checks authentication where it applies, validates
 * its input, and the AI endpoint enforces a per-user daily quota.
 */
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const crypto = require("crypto");

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");
const OPENWEATHER_API_KEY = defineSecret("OPENWEATHER_API_KEY");
const PERSPECTIVE_API_KEY = defineSecret("PERSPECTIVE_API_KEY");

// Set with: firebase functions:config or a .env file. Verify the name against OpenAI's model list.
const OPENAI_MODEL = defineString("OPENAI_MODEL", { default: "gpt-5.6-terra" });

const SCOUT_DAILY_LIMIT = 40;
const LOOKUP_HOURLY_LIMIT = 10;

const SCOUT_PERSONALITY = `You are Scout, an enthusiastic and expert outdoor adventure guide for PATHY.

Expertise: hiking, mountain biking, cycling, offroad trails, weather-aware planning, safety, navigation, gear, and PATHY app features (map, groups, walkie-talkie, trail discovery).

Rules:
- Prioritise user safety. Never recommend dangerous activities or ignore weather warnings.
- Warn when a trail is beyond the user's skill level.
- For medical or emergency questions, tell the user to call emergency services.
- Keep answers concise (2-4 sentences) and actionable. Use an emoji now and then.`;

// The app language the user picked (sent by the client as "lang"). Scout answers in it.
const SCOUT_LANGUAGES = { en: "English", ar: "Arabic", he: "Hebrew" };

function languageInstruction(lang) {
  const name = SCOUT_LANGUAGES[lang];
  if (!name) return "";
  const extra =
    lang === "en"
      ? ""
      : " Write natural, correct " +
        name +
        " (Modern Standard style for Arabic) and address the user in a gender-neutral way where the language allows. Keep place names, trail names and units such as km and m as the user wrote them.";
  return `Language: reply in ${name}, the language of the app the user chose, unless the user clearly writes to you in another language.${extra}`;
}

// ---------- helpers ----------

function cleanText(value, maxLength) {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, maxLength);
}

function sanitizeHistory(value) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((m) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
    .slice(-10)
    .map((m) => ({ role: m.role, content: m.content.slice(0, 1000) }));
}

function sanitizeLocation(lat, lng) {
  if (typeof lat !== "number" || typeof lng !== "number") return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
  return { lat, lng };
}

function requireAuth(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  return request.auth.uid;
}

/** Normalises a phone number to digits only (e.g. "+972 50-123-4567" -> "972501234567"). */
function normalizePhone(value) {
  const digits = String(value ?? "").replace(/\D/g, "");
  return digits.length >= 7 && digits.length <= 15 ? digits : null;
}

function hashPhone(digits) {
  return crypto.createHash("sha256").update(digits).digest("hex");
}

async function fetchWithTimeout(url, options, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/** Atomically counts one AI request for the user today; throws when the daily limit is reached. */
async function consumeScoutQuota(uid) {
  const day = new Date().toISOString().slice(0, 10);
  const ref = getFirestore().collection("ai_usage").doc(`${uid}_${day}`);
  await getFirestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.exists ? snap.get("count") : 0;
    if (count >= SCOUT_DAILY_LIMIT) {
      throw new HttpsError("resource-exhausted", "Daily Scout limit reached. Try again tomorrow.");
    }
    tx.set(ref, { count: count + 1, uid, day, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  });
}

async function fetchWeather(location) {
  try {
    const url =
      "https://api.openweathermap.org/data/2.5/weather" +
      `?lat=${location.lat}&lon=${location.lng}&units=metric` +
      `&appid=${encodeURIComponent(OPENWEATHER_API_KEY.value())}`;
    const res = await fetchWithTimeout(url, {}, 8000);
    if (!res.ok) return null;
    const json = await res.json();
    return {
      temperature: json.main?.temp,
      description: json.weather?.[0]?.description,
      windKmh: typeof json.wind?.speed === "number" ? json.wind.speed * 3.6 : undefined,
    };
  } catch (err) {
    console.warn("weather lookup failed", err?.message);
    return null;
  }
}

async function callOpenAI(messages) {
  let res;
  try {
    res = await fetchWithTimeout(
      "https://api.openai.com/v1/chat/completions",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${OPENAI_API_KEY.value()}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: OPENAI_MODEL.value(),
          messages,
          max_completion_tokens: 400,
        }),
      },
      30000
    );
  } catch (err) {
    console.error("OpenAI request failed", err?.name);
    throw new HttpsError("unavailable", "Scout is unavailable right now.");
  }
  if (!res.ok) {
    console.error("OpenAI returned HTTP", res.status);
    throw new HttpsError("unavailable", "Scout is unavailable right now.");
  }
  const json = await res.json();
  const text = json?.choices?.[0]?.message?.content?.trim();
  if (!text) {
    throw new HttpsError("internal", "Scout returned an empty answer.");
  }
  return text;
}

// ---------- callables ----------

/**
 * Scout chat. Input: { message, history?: [{role, content}], lat?, lng?, lang?: "en" | "ar" | "he" }
 * Output: { message }
 */
exports.scoutChat = onCall(
  { secrets: [OPENAI_API_KEY, OPENWEATHER_API_KEY], timeoutSeconds: 60, memory: "256MiB" },
  async (request) => {
    const uid = requireAuth(request);
    const message = cleanText(request.data?.message, 1000);
    if (!message) {
      throw new HttpsError("invalid-argument", "message is required.");
    }
    const history = sanitizeHistory(request.data?.history);
    const location = sanitizeLocation(request.data?.lat, request.data?.lng);

    await consumeScoutQuota(uid);

    const weather = location ? await fetchWeather(location) : null;
    const now = new Date().toISOString();
    const context = [
      `Current UTC time: ${now}`,
      location ? `User location: ${location.lat.toFixed(4)}, ${location.lng.toFixed(4)}` : null,
      weather
        ? `Current weather: ${weather.description ?? "unknown"}, ${weather.temperature ?? "?"}°C, wind ${Math.round(weather.windKmh ?? 0)} km/h`
        : null,
    ]
      .filter(Boolean)
      .join("\n");

    const messages = [
      {
        role: "system",
        content: [SCOUT_PERSONALITY, languageInstruction(request.data?.lang), `Context:\n${context}`]
          .filter(Boolean)
          .join("\n\n"),
      },
      ...history,
      { role: "user", content: message },
    ];

    const reply = await callOpenAI(messages);
    return { message: reply };
  }
);

/**
 * Current weather for the Outdoor Weather screens. Input: { lat, lng }.
 * Output: the OpenWeather "current weather" payload (main, weather, wind, clouds, sys, rain, snow).
 */
exports.currentWeather = onCall(
  { secrets: [OPENWEATHER_API_KEY], timeoutSeconds: 20 },
  async (request) => {
    requireAuth(request);
    const location = sanitizeLocation(request.data?.lat, request.data?.lng);
    if (!location) {
      throw new HttpsError("invalid-argument", "lat and lng are required.");
    }
    const url =
      "https://api.openweathermap.org/data/2.5/weather" +
      `?lat=${location.lat}&lon=${location.lng}&units=metric` +
      `&appid=${encodeURIComponent(OPENWEATHER_API_KEY.value())}`;
    let res;
    try {
      res = await fetchWithTimeout(url, {}, 8000);
    } catch (err) {
      console.error("OpenWeather request failed", err?.name);
      throw new HttpsError("unavailable", "Weather is unavailable right now.");
    }
    if (!res.ok) {
      console.error("OpenWeather returned HTTP", res.status);
      throw new HttpsError("unavailable", "Weather is unavailable right now.");
    }
    const json = await res.json();
    return {
      main: json.main,
      weather: json.weather,
      wind: json.wind,
      clouds: json.clouds,
      sys: json.sys,
      rain: json.rain,
      snow: json.snow,
    };
  }
);

/**
 * Content moderation via Perspective API. Input: { text }. Output: { decision: "OK"|"NEEDS_EDIT"|"BLOCKED" }
 * Throws "unavailable" when Perspective cannot be reached so the app can fall back to local rules.
 */
exports.moderateText = onCall(
  { secrets: [PERSPECTIVE_API_KEY], timeoutSeconds: 20 },
  async (request) => {
    requireAuth(request);
    const text = cleanText(request.data?.text, 2000);
    if (!text) return { decision: "OK" };

    let res;
    try {
      res = await fetchWithTimeout(
        "https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": PERSPECTIVE_API_KEY.value(),
          },
          body: JSON.stringify({
            comment: { text },
            languages: ["he", "en", "ar"],
            requestedAttributes: {
              TOXICITY: {},
              INSULT: {},
              THREAT: {},
              IDENTITY_ATTACK: {},
              SEVERE_TOXICITY: {},
            },
          }),
        },
        8000
      );
    } catch (err) {
      console.error("Perspective request failed", err?.name);
      throw new HttpsError("unavailable", "Moderation unavailable.");
    }
    if (!res.ok) {
      console.error("Perspective returned HTTP", res.status);
      throw new HttpsError("unavailable", "Moderation unavailable.");
    }
    const json = await res.json();
    const toxicity = json.attributeScores?.TOXICITY?.summaryScore?.value ?? 0;
    if (toxicity >= 0.85) return { decision: "BLOCKED" };
    if (toxicity >= 0.7) return { decision: "NEEDS_EDIT" };
    return { decision: "OK" };
  }
);

/**
 * Stores the signed-in user's phone number privately and maintains the phone -> uid lookup used
 * for phone-number sign-in. The phone number is never written to the public users document.
 * Input: { phone }
 */
exports.saveContactInfo = onCall(async (request) => {
  const uid = requireAuth(request);
  const digits = normalizePhone(request.data?.phone);
  if (!digits) {
    throw new HttpsError("invalid-argument", "Enter a valid phone number.");
  }
  const db = getFirestore();
  const contactRef = db.collection("users").doc(uid).collection("private").doc("contact");
  const newHash = hashPhone(digits);

  const previous = await contactRef.get();
  const oldHash = previous.exists ? previous.get("phoneHash") : null;

  const batch = db.batch();
  batch.set(contactRef, { phone: digits, phoneHash: newHash, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  batch.set(db.collection("phone_index").doc(newHash), { uid });
  if (oldHash && oldHash !== newHash) {
    batch.delete(db.collection("phone_index").doc(oldHash));
  }
  await batch.commit();
  return { ok: true };
});

/**
 * Resolves the login email for a phone number. Callable without sign-in because it runs before login.
 * Limited to a few lookups per phone number per hour to slow enumeration.
 * Input: { phone }. Output: { email } (null when no account matches).
 */
exports.resolveLoginEmail = onCall({ maxInstances: 5 }, async (request) => {
  const digits = normalizePhone(request.data?.phone);
  if (!digits) {
    throw new HttpsError("invalid-argument", "Enter a valid phone number.");
  }
  const db = getFirestore();
  const hash = hashPhone(digits);
  const hour = new Date().toISOString().slice(0, 13);
  const limitRef = db.collection("auth_lookup_limits").doc(`${hash}_${hour}`);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(limitRef);
    const count = snap.exists ? snap.get("count") : 0;
    if (count >= LOOKUP_HOURLY_LIMIT) {
      throw new HttpsError("resource-exhausted", "Too many attempts. Try again later.");
    }
    tx.set(limitRef, { count: count + 1, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  });

  const index = await db.collection("phone_index").doc(hash).get();
  if (!index.exists) return { email: null };
  try {
    const user = await getAuth().getUser(index.get("uid"));
    return { email: user.email ?? null };
  } catch (err) {
    console.warn("resolveLoginEmail: user lookup failed", err?.code);
    return { email: null };
  }
});

const REPORT_POINTS = 5;
const REPORT_POINTS_DAILY_LIMIT = 20; // reports per day that earn points

/**
 * Stores a map report and awards points. Points are granted here, never by the app, so a client
 * cannot raise its own reportPoints. Input: { type, category, description, latitude, longitude }.
 * Output: { id, pointsAwarded }
 */
exports.submitReport = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = request.data || {};
  const type = data.type === "HAZARD" || data.type === "POINT_OF_INTEREST" ? data.type : null;
  const category = cleanText(data.category, 60);
  const description = cleanText(data.description, 500);
  const location = sanitizeLocation(data.latitude, data.longitude);
  if (!type || !category || !location) {
    throw new HttpsError("invalid-argument", "Invalid report.");
  }

  const db = getFirestore();
  const day = new Date().toISOString().slice(0, 10);
  const pointsRef = db.collection("report_points").doc(`${uid}_${day}`);
  const reportRef = db.collection("reports").doc();
  const userRef = db.collection("users").doc(uid);
  let pointsAwarded = 0;

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(pointsRef);
    const count = snap.exists ? snap.get("count") : 0;
    tx.set(reportRef, {
      type,
      category,
      description,
      latitude: location.lat,
      longitude: location.lng,
      timestamp: Date.now(),
      userId: uid,
    });
    if (count < REPORT_POINTS_DAILY_LIMIT) {
      tx.set(pointsRef, { count: count + 1, uid, day, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      tx.set(userRef, { reportPoints: FieldValue.increment(REPORT_POINTS) }, { merge: true });
      pointsAwarded = REPORT_POINTS;
    }
  });

  return { id: reportRef.id, pointsAwarded };
});
