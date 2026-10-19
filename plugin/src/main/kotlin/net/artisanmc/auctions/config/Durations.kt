package net.artisanmc.auctions.config

import java.time.Duration

/** `90s`, `15m`, `48h`, `30d`, or a sum like `1h30m`. Null when unreadable or zero. */
fun parseDuration(text: String): Duration? {
    val src = text.trim().lowercase()
    if (src.isEmpty()) return null
    var total = Duration.ZERO
    var rest = src
    val part = Regex("^(\\d+)\\s*([smhd])\\s*")
    while (rest.isNotEmpty()) {
        val m = part.find(rest) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        total += when (m.groupValues[2]) {
            "s" -> Duration.ofSeconds(n)
            "m" -> Duration.ofMinutes(n)
            "h" -> Duration.ofHours(n)
            else -> Duration.ofDays(n)
        }
        rest = rest.substring(m.range.last + 1)
    }
    return total.takeIf { !it.isZero }
}

/** Inverse of [parseDuration], largest units first: `PT25H` → `1d1h`. */
fun formatDuration(d: Duration): String {
    if (d.isZero) return "0s"
    val parts = listOf(d.toDaysPart() to "d", d.toHoursPart().toLong() to "h",
        d.toMinutesPart().toLong() to "m", d.toSecondsPart().toLong() to "s")
    return parts.filter { it.first > 0 }.joinToString("") { "${it.first}${it.second}" }
}
