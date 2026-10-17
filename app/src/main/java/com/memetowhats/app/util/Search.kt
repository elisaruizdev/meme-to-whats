package com.memetowhats.app.util

import java.text.Normalizer

private val diacritics = Regex("\\p{M}+")
private val whitespace = Regex("\\s+")

/** Minúsculas y sin tildes, para que "cancion" encuentre "Canción". */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(diacritics, "")

/** true si todas las palabras de [query] aparecen en alguno de los [fields]. */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val words = query.normalizedForSearch().split(whitespace).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = fields.filterNotNull().joinToString(" ").normalizedForSearch()
    return words.all { it in haystack }
}
