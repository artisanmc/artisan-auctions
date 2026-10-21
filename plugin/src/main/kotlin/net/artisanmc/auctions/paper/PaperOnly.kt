package net.artisanmc.auctions.paper

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import java.util.Base64

/**
 * Every call into Paper's own API (Adventure components, byte serialization,
 * Paper profiles). Loaded only when [Platform.paper]: a class that names these
 * types in its methods fails to load on Spigot, even if the methods never run.
 */
internal object PaperOnly {
    private val mini = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()

    fun encode(stack: ItemStack): String = Base64.getEncoder().encodeToString(stack.serializeAsBytes())

    fun decode(base64: String): ItemStack = ItemStack.deserializeBytes(Base64.getDecoder().decode(base64))

    private fun name(meta: ItemMeta): Component? = when {
        meta.hasDisplayName() -> meta.displayName()
        meta.hasItemName() -> meta.itemName()
        else -> null
    }

    fun nameMini(meta: ItemMeta): String? = name(meta)?.let(mini::serialize)

    fun namePlain(meta: ItemMeta): String? = name(meta)?.let(plain::serialize)

    fun itemNamePlain(meta: ItemMeta): String? = meta.takeIf { it.hasItemName() }?.itemName()?.let(plain::serialize)

    fun customNamePlain(meta: ItemMeta): String? = meta.takeIf { it.hasDisplayName() }?.displayName()?.let(plain::serialize)

    fun loreMini(meta: ItemMeta): List<String> = meta.lore()?.map(mini::serialize).orEmpty()

    fun lorePlain(meta: ItemMeta): List<String> = meta.lore()?.map(plain::serialize).orEmpty()

    /** The `textures` property as Mojang signed it, untouched. */
    fun headTextures(meta: SkullMeta): String? = meta.playerProfile?.properties?.firstOrNull { it.name == "textures" }?.value

    /** True when [message] was a component and has been sent. */
    fun sendComponent(player: Player, message: Any?): Boolean {
        if (message !is Component) return false
        player.sendMessage(message)
        return true
    }
}

/** Paper (and its forks) or Spigot, decided once by the presence of a Paper-only class. */
object Platform {
    @JvmStatic
    var paper: Boolean = runCatching { Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler") }.isSuccess
        internal set
}
