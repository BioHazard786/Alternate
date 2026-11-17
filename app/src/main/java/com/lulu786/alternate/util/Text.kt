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
