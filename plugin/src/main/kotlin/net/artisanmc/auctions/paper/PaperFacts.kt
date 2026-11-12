package net.artisanmc.auctions.paper

import net.artisanmc.auctions.median.ItemFacts
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.lang.reflect.Method

/** Reads from a stack what [net.artisanmc.auctions.median.PriceKeys] needs. */
object PaperFacts {
    fun of(stack: ItemStack): ItemFacts {
        val meta = stack.itemMeta
        val pdc = meta?.persistentDataContainer?.let(::entries).orEmpty()
        return ItemFacts(
            material = stack.type.name,
            pluginIds = listOfNotNull(itemsAdderId(stack)?.let { "itemsadder" to it }).toMap(),
            pdc = pdc,
            itemName = meta?.let(PaperItems::plainItemName),
            customName = meta?.let(PaperItems::plainCustomName),
            lore = PaperItems.plainLore(stack),
            enchants = stack.enchantments.entries.associate { (e, level) -> e.key.key to level },
            potion = (meta as? PotionMeta)?.basePotionType?.key?.key,
        )
    }

    private val TYPES: List<PersistentDataType<*, *>> = listOf(
        PersistentDataType.STRING, PersistentDataType.INTEGER, PersistentDataType.LONG,
        PersistentDataType.DOUBLE, PersistentDataType.BYTE, PersistentDataType.FLOAT, PersistentDataType.SHORT,
    )

    private fun entries(pdc: PersistentDataContainer): Map<String, String> = pdc.keys.associate { key ->
        val value = TYPES.firstNotNullOfOrNull { type -> runCatching { pdc.get(key, type) }.getOrNull() }
        key.toString().lowercase() to (value?.toString() ?: "?")
    }

    // ItemsAdder keeps its id outside the PDC; its API is read reflectively so it stays optional.
    private val byItemStack: Method? by lazy {
        runCatching { Class.forName("dev.lone.itemsadder.api.CustomStack").getMethod("byItemStack", ItemStack::class.java) }.getOrNull()
    }

    private fun itemsAdderId(stack: ItemStack): String? = runCatching {
        val custom = byItemStack?.invoke(null, stack) ?: return null
        custom.javaClass.getMethod("getNamespacedID").invoke(custom) as? String
    }.getOrNull()
}
