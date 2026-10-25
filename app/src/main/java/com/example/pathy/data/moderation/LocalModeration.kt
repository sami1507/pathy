package com.example.pathy.data.moderation

import java.text.Normalizer

// סינון מקומי מהיר – בלי רשת. משודרג כדי לתפוס יותר וריאציות של מילים פוגעניות.
object LocalModeration {

    // בסיס לרשימה שחורה – הרחב לפי הצורך (עברית/אנגלית/ערבית)
    private val rawBlacklist = setOf(
        "בן זונה", "שרמוטה", "חרא", // עברית
        "fucking", "fuck", "bitch","asshole","cunt","قحبة","شرموطة","הומו","זונה","מזדין", "תחת שלי", "هومو","منيك"
    ,"מניק"
    )

    // מיפוי ליט-ספיק (מספרים/תוים דומים)
    private val leetMap = mapOf(
        '0' to 'o','1' to 'i','3' to 'e','4' to 'a','5' to 's','7' to 't',
        '@' to 'a', '$' to 's'
    )

    // תווים בלתי נראים (Zero-width וכו’)
    private val zeroWidthRegex = Regex("[\\u200B-\\u200D\\u2060\\uFEFF]")

    // נכין את הרשימה השחורה מנורמלת מראש
    private val blacklist: Set<String> = rawBlacklist.map { normalize(it) }.toSet()

    // נורמליזציה חזקה
    private fun normalize(s: String): String {
        var t = s

        // Unicode normalize
        t = Normalizer.normalize(t, Normalizer.Form.NFKC)

        // הסרת תווים בלתי נראים
        t = zeroWidthRegex.replace(t, "")

        // הסרת ניקוד/Combining marks (כולל ניקוד עברי)
        t = t.replace(Regex("\\p{M}+"), "")

        // החלפת ליט-ספיק
        t = t.map { c -> leetMap[c] ?: c }.joinToString("")

        // לאותיות קטנות
        t = t.lowercase()

        // הסרת סימנים – להשאיר אותיות/ספרות/רווחים
        t = t.replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")

        // כיווץ חזרות אותיות (הוווו → הה)
        t = t.replace(Regex("(\\p{L})\\1{2,}"), "$1$1")

        // כיווץ רווחים
        t = t.replace(Regex("\\s+"), " ").trim()

        return t
    }

    // גרסה דחוסה (ללא רווחים) כדי לתפוס כתיבה מפורקת
    private fun compact(s: String): String = s.replace(" ", "")

    // בדיקה מקומית – אם נמצאו מילים אסורות -> NEEDS_EDIT
    fun check(text: String): ModerationDecision {
        val norm = normalize(text)
        val compactNorm = compact(norm)

        // חיפוש עם "גבולות מילה" – שלא נתפוס חלק ממילה תמימה
        fun containsAsWord(haystack: String, needle: String): Boolean {
            val r = Regex("(?<!\\p{L})" + Regex.escape(needle) + "(?!\\p{L})")
            return r.containsMatchIn(haystack)
        }

        val hit = blacklist.any { bad ->
            containsAsWord(norm, bad) ||
                    containsAsWord(compactNorm, bad) ||
                    Regex(bad.toCharArray().joinToString("\\s*")).containsMatchIn(norm)
        }

        return if (hit) ModerationDecision.NEEDS_EDIT else ModerationDecision.OK
    }
}
