package com.lulu786.alternate.util

import java.text.Normalizer

private val marks = Regex("\\p{Mn}+")

/** Lowercase, accent-free key so "Émile" sorts and searches next to "Emile". */
fun sortKeyOf(name: String): String =
    Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replace(marks, "").lowercase()

/** Section letter for the A-Z list; anything that is not a letter goes under '#'. */
fun sectionOf(sortKey: String): Char = sortKey.firstOrNull()?.takeIf { it in 'a'..'z' }?.uppercaseChar() ?: '#'

fun displayName(prefix: String, name: String, suffix: String): String = buildString {
    if (prefix.isNotBlank()) append(prefix.trim()).append(' ')
    append(name.trim())
    if (suffix.isNotBlank()) append(", ").append(suffix.trim())
}

/** A number saved "as typed" has an empty country code and no '+'; everything else is international digits. */
fun intl(full: String, countryCode: String?): String = if (countryCode == "") full else "+$full"

fun formatPhone(full: String, iso: String?): String = if (iso == "") full else
    android.telephony.PhoneNumberUtils.formatNumber("+$full", iso?.takeIf { it.isNotEmpty() } ?: java.util.Locale.getDefault().country) ?: "+$full"

/** "--03-12" -> "12 March"; "1990-03-12" -> "12 March 1990 · 36 years". Parsed as a plain date, so no time-zone off-by-one. */
fun formatBirthday(value: String, today: java.time.LocalDate = java.time.LocalDate.now()): String? = runCatching {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM")
    if (value.startsWith("--")) {
        java.time.MonthDay.of(value.substring(2, 4).toInt(), value.substring(5, 7).toInt()).format(fmt)
    } else {
        val d = java.time.LocalDate.parse(value)
        "${d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))} · ${java.time.Period.between(d, today).years} years"
    }
}.getOrNull()
