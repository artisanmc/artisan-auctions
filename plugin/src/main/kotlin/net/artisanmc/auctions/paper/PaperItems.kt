package net.artisanmc.auctions.paper

import net.artisanmc.modules.api.text.TextApi
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.inventory.meta.SkullMeta
import java.util.Base64

/**
 * Items as the add-on stores and shows them. On Paper the stored form is
 * Paper's own byte serialization, which keeps every component and upgrades
 * across versions; on Spigot it is Bukkit's YAML serialization behind a
 * `yaml:` prefix. Both decode on Paper, only the second on Spigot.
 * The display form is an Artisan icon map (`item: "{l.icon}"` in a menu).
 */
object PaperItems {
    private const val YAML = "yaml:"

    /** Converts Spigot's `§` strings to MiniMessage; set from the Artisan API at enable. */
    @Volatile
    var text: TextApi = TextApi.NOOP

    fun encode(stack: ItemStack): String =
        if (Platform.paper) PaperOnly.encode(stack)
        else YAML + Base64.getEncoder().encodeToString(YamlConfiguration().apply { set("item", stack) }.saveToString().toByteArray())

    fun decode(blob: String): ItemStack {
        if (!blob.startsWith(YAML)) {
            check(Platform.paper) { "item stored by Paper, unreadable on Spigot" }
            return PaperOnly.decode(blob)
        }
        val yaml = String(Base64.getDecoder().decode(blob.substring(YAML.length)))
        return YamlConfiguration().apply { loadFromString(yaml) }.getItemStack("item") ?: error("no item in blob")
    }

    fun decodeOrNull(blob: String): ItemStack? = runCatching { decode(blob) }.getOrNull()

    @Suppress("DEPRECATION") // the String methods are the only ones Spigot has
    private fun legacyName(meta: ItemMeta): String? = when {
        meta.hasDisplayName() -> meta.displayName
        meta.hasItemName() -> meta.itemName
        else -> null
    }

    @Suppress("DEPRECATION")
    private fun legacyLore(meta: ItemMeta): List<String> = meta.lore.orEmpty()

    private fun strip(legacy: String) = ChatColor.stripColor(legacy) ?: legacy

    fun plainLore(stack: ItemStack): List<String> {
        val meta = stack.itemMeta ?: return emptyList()
        return if (Platform.paper) PaperOnly.lorePlain(meta) else legacyLore(meta).map(::strip)
    }

    /** The name a player sees, without formatting: custom name, else item name, else null (vanilla). */
    fun plainName(stack: ItemStack): String? {
        val meta = stack.itemMeta ?: return null
        return if (Platform.paper) PaperOnly.namePlain(meta) else legacyName(meta)?.let(::strip)
    }

    @Suppress("DEPRECATION")
    fun plainItemName(meta: ItemMeta): String? =
        if (Platform.paper) PaperOnly.itemNamePlain(meta) else meta.takeIf { it.hasItemName() }?.itemName?.let(::strip)

    @Suppress("DEPRECATION")
    fun plainCustomName(meta: ItemMeta): String? =
        if (Platform.paper) PaperOnly.customNamePlain(meta) else meta.takeIf { it.hasDisplayName() }?.displayName?.let(::strip)

    /** Icon map with the keys Artisan's menus understand. */
    fun display(stack: ItemStack): Map<String, Any?> {
        val meta = stack.itemMeta
        val out = LinkedHashMap<String, Any?>()
        out["material"] = stack.type.name
        out["count"] = stack.amount.coerceIn(1, 64)
        if (meta != null) {
            val title = if (Platform.paper) PaperOnly.nameMini(meta) else legacyName(meta)?.let(text::fromLegacy)
            title?.let { out["title"] = it }
            val lore = if (Platform.paper) PaperOnly.loreMini(meta) else legacyLore(meta).map(text::fromLegacy)
            if (lore.isNotEmpty()) out["lore"] = lore
        }
        val enchants = stack.enchantments.entries.associate { (e, level) -> e.key.key to level }
        if (enchants.isNotEmpty()) out["enchants"] = enchants
        else if (meta != null && meta.hasEnchantmentGlintOverride() && meta.enchantmentGlintOverride) out["enchanted"] = true
        if (meta != null) {
            @Suppress("DEPRECATION")
            if (meta.hasCustomModelData()) out["custom_model_data"] = meta.customModelData.toString()
            if (meta.isUnbreakable) out["unbreakable"] = true
        }
        when (meta) {
            is SkullMeta -> headTextures(meta)?.let { out["head"] = "basehead:$it" }
            is LeatherArmorMeta -> out["color"] = hex(meta.color.asRGB())
            is PotionMeta -> meta.color?.let { out["color"] = hex(it.asRGB()) }
            else -> {}
        }
        return out
    }

    /** Spigot has no signed property: the skin URL is re-encoded the way Mojang writes it. */
    private fun headTextures(meta: SkullMeta): String? {
        if (Platform.paper) return PaperOnly.headTextures(meta)
        val url = meta.ownerProfile?.textures?.skin ?: return null
        return Base64.getEncoder().encodeToString("""{"textures":{"SKIN":{"url":"$url"}}}""".toByteArray())
    }

    private fun hex(rgb: Int) = "#%06x".format(rgb and 0xFFFFFF)
}
