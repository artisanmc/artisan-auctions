package net.artisanmc.auctions.median

import net.artisanmc.auctions.config.AuctionsConfig
import java.security.MessageDigest

/** What the price key reads from an item, gathered by the Paper layer. All text is plain (no formatting). */
data class ItemFacts(
    val material: String,
    /** Identifiers set by item plugins, e.g. `nexo` → `excalibur`. */
    val pluginIds: Map<String, String> = emptyMap(),
    /** Every persistent data entry, `namespace:key` → value as text. */
    val pdc: Map<String, String> = emptyMap(),
    /** The `item_name` component: not editable with an anvil. */
    val itemName: String? = null,
    /** The `custom_name` component: anvil-editable. */
    val customName: String? = null,
    val lore: List<String> = emptyList(),
    val enchants: Map<String, Int> = emptyMap(),
    val potion: String? = null,
)

/**
 * "Is this the same item?" for the median. The first signal present wins:
 * 1. an item plugin's id or a declared PDC key, 2. `item_name` + lore,
 * 3. `custom_name` + lore (only with a lore), 4. material + enchantments.
 * Keys are at most 255 characters (the column size).
 */
object PriceKeys {
    const val VANILLA = "vanilla:"
    const val CUSTOM = "custom:"

    /** PDC keys item plugins use for their ids, in the order they are tried. */
    val PLUGIN_ID_KEYS = listOf("nexo:id", "oraxen:id", "mmoitems:mmoitems_item_id", "executableitems:ei-id")

    fun isCustom(key: String) = !key.startsWith(VANILLA)

    fun of(f: ItemFacts, m: AuctionsConfig.Median): String {
        val mat = f.material.lowercase()
        if (m.key != "vanilla") {
            f.pluginIds.entries.sortedBy { it.key }.firstOrNull()?.let { (plugin, id) -> return clip("$CUSTOM$plugin:$id") }
            for (k in PLUGIN_ID_KEYS + m.customIdKeys) {
                f.pdc[k.lowercase()]?.let { return clip("$CUSTOM$k=$it") }
            }
            val lore = normalizedLore(f.lore, m.loreIgnore)
            val name = f.itemName?.trim()?.takeIf { it.isNotEmpty() }
                ?: f.customName?.trim()?.takeIf { it.isNotEmpty() && lore.isNotEmpty() }
            if (name != null) {
                return clip(buildString {
                    append(CUSTOM).append(mat)
                    append("#name=").append(if (name.length > 48) hash(name) else name)
                    if (lore.isNotEmpty()) append("#lore=").append(hash(lore.joinToString("\n")))
                    attributes(f, m)?.let { append("#attr=").append(it) }
                    if (m.customIncludeEnchants && f.enchants.isNotEmpty()) append("#ench=").append(hash(enchants(f)))
                })
            }
        }
        return clip(buildString {
            append(VANILLA).append(mat)
            if (f.enchants.isNotEmpty()) append('#').append(enchants(f))
            f.potion?.let { append("#potion=").append(it.lowercase()) }
        })
    }

    fun normalizedLore(lore: List<String>, ignore: List<Regex>): List<String> =
        lore.map { it.trim() }.filter { line -> line.isNotEmpty() && ignore.none { it.containsMatchIn(line) } }

    private fun enchants(f: ItemFacts) = f.enchants.entries.sortedBy { it.key }.joinToString(",") { "${it.key.lowercase()}=${it.value}" }

    private fun attributes(f: ItemFacts, m: AuctionsConfig.Median): String? {
        val entries = f.pdc.filterKeys { it !in PLUGIN_ID_KEYS }.toSortedMap()
        if (entries.isEmpty()) return null
        return when (m.attributes) {
            "keys" -> hash(entries.keys.joinToString(","))
            "values" -> hash(entries.entries.joinToString(",") { "${it.key}=${it.value}" })
            else -> null
        }
    }

    fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    private fun clip(key: String) = if (key.length <= 255) key else key.take(238) + "#" + hash(key)
}
