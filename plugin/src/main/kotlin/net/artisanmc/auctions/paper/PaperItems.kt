package net.artisanmc.auctions.paper

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.inventory.meta.SkullMeta
import java.util.Base64

/**
 * Items as the add-on stores and shows them. The stored form is Paper's own
 * byte serialization, which keeps every component and upgrades across versions.
 * The display form is an Artisan icon map (`item: "{l.icon}"` in a menu).
 */
object PaperItems {
    private val mini = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()

    fun encode(stack: ItemStack): String = Base64.getEncoder().encodeToString(stack.serializeAsBytes())

    fun decode(blob: String): ItemStack = ItemStack.deserializeBytes(Base64.getDecoder().decode(blob))

    fun decodeOrNull(blob: String): ItemStack? = runCatching { decode(blob) }.getOrNull()

    /** The name a player sees: custom name, else item name, else null (vanilla). */
    fun nameComponent(stack: ItemStack): Component? {
        val meta = stack.itemMeta ?: return null
        return when {
            meta.hasDisplayName() -> meta.displayName()
            meta.hasItemName() -> meta.itemName()
            else -> null
        }
    }

    fun plainLore(stack: ItemStack): List<String> = stack.itemMeta?.lore()?.map(plain::serialize).orEmpty()

    fun plainName(stack: ItemStack): String? = nameComponent(stack)?.let(plain::serialize)

    /** Icon map with the keys Artisan's menus understand. */
    fun display(stack: ItemStack): Map<String, Any?> {
        val meta = stack.itemMeta
        val out = LinkedHashMap<String, Any?>()
        out["material"] = stack.type.name
        out["count"] = stack.amount.coerceIn(1, 64)
        nameComponent(stack)?.let { out["title"] = mini.serialize(it) }
        meta?.lore()?.takeIf { it.isNotEmpty() }?.let { lines -> out["lore"] = lines.map(mini::serialize) }
        val enchants = stack.enchantments.entries.associate { (e, level) -> e.key.key to level }
        if (enchants.isNotEmpty()) out["enchants"] = enchants
        else if (meta != null && meta.hasEnchantmentGlintOverride() && meta.enchantmentGlintOverride) out["enchanted"] = true
        if (meta != null) {
            @Suppress("DEPRECATION")
            if (meta.hasCustomModelData()) out["custom_model_data"] = meta.customModelData.toString()
            if (meta.isUnbreakable) out["unbreakable"] = true
        }
        when (meta) {
            is SkullMeta -> meta.playerProfile?.properties?.firstOrNull { it.name == "textures" }?.let {
                out["head"] = "basehead:${it.value}"
            }
            is LeatherArmorMeta -> out["color"] = hex(meta.color.asRGB())
            is PotionMeta -> meta.color?.let { out["color"] = hex(it.asRGB()) }
            else -> {}
        }
        return out
    }

    private fun hex(rgb: Int) = "#%06x".format(rgb and 0xFFFFFF)
}
