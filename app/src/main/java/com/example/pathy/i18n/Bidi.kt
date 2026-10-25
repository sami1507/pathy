package com.example.pathy.i18n

// First-strong isolate and pop-directional-isolate. Built from code points on purpose: writing the
// characters in the source would look like a "Trojan Source" attack to lint and to reviewers.
private val FSI = 0x2068.toChar()
private val PDI = 0x2069.toChar()

/**
 * Text typed by users (names, trail titles) is dropped into sentences that may run the other way,
 * for example a Latin trail name inside an Arabic sentence. Wrapping it in first-strong isolates
 * keeps the name in its own direction and stops it from reordering the words and punctuation
 * around it.
 */
fun String.isolateBidi(): String = buildString(length + 2) {
    append(FSI)
    append(this@isolateBidi)
    append(PDI)
}
