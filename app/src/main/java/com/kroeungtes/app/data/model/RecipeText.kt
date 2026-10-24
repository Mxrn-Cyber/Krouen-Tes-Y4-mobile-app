package com.kroeungtes.app.data.model

import java.util.Locale

/** Helpers for turning raw recipe text into the interactive pieces of the Recipe page. */
object RecipeText {

    private val fractions = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)
    private val leadingNumber = Regex("""^\s*(\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?)(.*)$""")

    /**
     * Multiplies the number at the start of an amount ("200g", "1 1/2 cups", "½ tsp").
     * Amounts with no number ("to taste", "pinch") are returned unchanged.
     */
    fun scale(amount: String, factor: Int): String {
        if (factor == 1 || amount.isBlank()) return amount
        var text = amount.trim()
        // Unicode fraction at the start, maybe after a whole number: "1½ cups"
        Regex("""^(\d*)\s*([½¼¾⅓⅔⅛])(.*)$""").find(text)?.let { m ->
            val whole = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val value = whole + (fractions[m.groupValues[2][0]] ?: 0.0)
            return format(value * factor) + m.groupValues[3]
        }
        val m = leadingNumber.find(text) ?: return amount
        val value = parse(m.groupValues[1]) ?: return amount
        text = format(value * factor) + m.groupValues[2]
        return text
    }

    private fun parse(token: String): Double? {
        val t = token.trim().replace(',', '.')
        return when {
            t.contains(' ') -> {
                val (w, f) = t.split(Regex("\\s+"), limit = 2)
                (w.toDoubleOrNull() ?: return null) + (parse(f) ?: return null)
            }
            t.contains('/') -> {
                val (a, b) = t.split('/', limit = 2)
                val den = b.toDoubleOrNull() ?: return null
                if (den == 0.0) null else (a.toDoubleOrNull() ?: return null) / den
            }
            else -> t.toDoubleOrNull()
        }
    }

    private fun format(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    /**
     * Splits TheMealDB instructions into steps: by line, dropping "STEP 1" labels and
     * leading numbers; one long paragraph is split into sentences.
     */
    fun steps(instructions: String?): List<String> {
        if (instructions.isNullOrBlank()) return emptyList()
        val label = Regex("""^(step\s*\d+[:.)-]?|\d+[.)])\s*""", RegexOption.IGNORE_CASE)
        var lines = instructions.split(Regex("""\r?\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { Regex("""^(step\s*)?\d+[:.)]?$""", RegexOption.IGNORE_CASE).matches(it) }
            .map { it.replaceFirst(label, "").trim() }
            .filter { it.isNotEmpty() }
        if (lines.size <= 1) {
            lines = (lines.firstOrNull() ?: instructions).split(Regex("""(?<=[.!?])\s+"""))
                .map { it.trim() }.filter { it.length > 1 }
        }
        return lines
    }
}
