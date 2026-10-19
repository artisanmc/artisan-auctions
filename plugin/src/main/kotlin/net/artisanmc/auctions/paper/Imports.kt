package net.artisanmc.auctions.paper

import net.artisanmc.auctions.importer.AuctionSource
import net.artisanmc.auctions.importer.ConvertedItem
import net.artisanmc.auctions.importer.Importer
import net.artisanmc.auctions.importer.ItemTranscoder
import net.artisanmc.auctions.importer.ZahV3Reader
import net.artisanmc.auctions.importer.ZahV4Reader
import net.artisanmc.auctions.median.PriceKeys
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.util.io.BukkitObjectInputStream
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.ByteArrayInputStream
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import java.util.Properties
import java.util.logging.Level
import java.util.zip.GZIPInputStream

/**
 * `/ah admin import <zauctionhouse|zauctionhouse-v3> [confirm]` (see [AdminCommands]). Reads the
 * source read-only, from its own settings; without `confirm` it is a dry run.
 */
class Imports(private val plugin: Plugin, private val house: AuctionHouse, private val economy: Currencies) {
    private val plugins = plugin.dataFolder.parentFile
    @Volatile private var running = false

    fun run(sender: CommandSender, which: String, confirm: Boolean) {
        if (which !in SOURCES) return sender.sendMessage("/ah admin import <${SOURCES.joinToString("|")}> [confirm]")
        Bukkit.getPluginManager().plugins.firstOrNull { it.isEnabled && it.name.startsWith("zAuctionHouse", ignoreCase = true) }?.let {
            return sender.sendMessage("${it.name} is running: remove it and restart first, or items could be taken on both sides.")
        }
        if (running) return sender.sendMessage("An import is already running.")
        running = true
        sender.sendMessage("${if (confirm) "Importing" else "Dry run of"} $which…")
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val lines = try {
                val source = source(which)
                val importer = Importer(house.server, house.store, transcoder(), ::currency)
                val report = importer.run(source.id, source.read(), dryRun = !confirm)
                if (!report.dryRun) {
                    val active = house.store.activeListings(house.server)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        house.cache.replaceAll(active)
                        house.medians.clear()
                        house.scheduleRefresh()
                    })
                }
                report.lines() + when {
                    report.blocked -> listOf("Nothing was imported.")
                    report.dryRun -> listOf("Run '/ah admin import $which confirm' to import.")
                    else -> listOf("Done.")
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "[Auctions] import from $which failed", e)
                listOf("Import failed: ${e.message}. Records written so far are kept; running it again resumes.")
            } finally {
                running = false
            }
            Bukkit.getScheduler().runTask(plugin, Runnable { lines.forEach(sender::sendMessage) })
        })
    }

    /** Source name → currency id: the configured map, else the same name if it is registered. */
    private fun currency(name: String): String? {
        house.config().import.currencyMap.entries.firstOrNull { it.key.equals(name, true) }?.let { return it.value.takeIf(economy::has) }
        return listOf(name, name.lowercase()).firstOrNull(economy::has)
    }

    private fun defaultEnd(): Long = System.currentTimeMillis() + house.config().sales.duration.toMillis()

    private fun source(which: String): AuctionSource {
        val zah = File(plugins, "zAuctionHouse")
        val config = yaml(File(zah, "config.yml"))
        return when (which) {
            "zauctionhouse" -> {
                // A database-configuration.yml in the server folder overrides the plugin's own.
                val db = (yaml(File("database-configuration.yml")).takeIf { it.isNotEmpty() }
                    ?: (config["database-configuration"] as? Map<*, *>).orEmpty())
                val prefix = db["table-prefix"]?.toString() ?: "zauctionhousev4_"
                val type = config["storage-type"]?.toString()?.uppercase() ?: "SQLITE"
                ZahV4Reader(connector(type, File(zah, "database.db"), db), prefix, ::defaultEnd)
            }
            else -> {
                val v3 = ((config["migration"] as? Map<*, *>)?.get("zauctionhouse-v3") as? Map<*, *>).orEmpty()
                val folder = v3["json-folder"]?.toString()?.let(::File)
                    ?: listOf("zAuctionHouseV3", "zAuctionHousev3").map { File(plugins, it) }.firstOrNull { it.isDirectory }
                    ?: File(plugins, "zAuctionHouseV3")
                val sqlite = v3["sqlite-path"]?.toString()?.let(::File) ?: File(folder, "database.db")
                val type = v3["source-type"]?.toString()?.uppercase() ?: if (sqlite.isFile) "SQLITE" else "JSON"
                if (type == "JSON") ZahV3Reader.json(folder, ::defaultEnd)
                else ZahV3Reader.sql(connector(type, sqlite, v3), v3["table-prefix"]?.toString() ?: "zauctionhouse_", ::defaultEnd)
            }
        }
    }

    private fun connector(type: String, sqlite: File, db: Map<*, *>): () -> Connection = when (type) {
        "SQLITE" -> {
            require(sqlite.isFile) { "${sqlite.path} not found" }
            ({
                Class.forName("org.sqlite.JDBC")
                DriverManager.getConnection("jdbc:sqlite:${sqlite.absolutePath}", Properties().apply { setProperty("open_mode", "1") })
            })
        }
        "MYSQL", "MARIADB" -> ({
            val url = "jdbc:mysql://${db["host"] ?: "localhost"}:${db["port"] ?: 3306}/${db["database"]}"
            DriverManager.getConnection(url, db["user"]?.toString(), db["password"]?.toString()).apply { isReadOnly = true }
        })
        else -> error("unsupported storage type $type")
    }

    private fun yaml(f: File): Map<*, *> = if (!f.isFile) emptyMap<Any, Any>() else
        runCatching { Yaml(SafeConstructor(LoaderOptions())).load<Any?>(f.readText()) as? Map<*, *> }.getOrNull().orEmpty()

    /**
     * zAuctionHouse stores Base64 of a GZIP'd Bukkit object stream (1.20.5+), optionally
     * marked `V2:`. The older NBT form (marked `NBT:` or not a Java stream) is not read:
     * those records stay in the source and are listed.
     */
    private fun transcoder() = ItemTranscoder { item ->
        val raw = item.raw.removePrefix("V2:")
        if (raw.startsWith("NBT:")) return@ItemTranscoder null
        val stack = runCatching {
            val bytes = GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(raw.trim()))).use { it.readBytes() }
            if (bytes.size < 2 || bytes[0] != 0xAC.toByte() || bytes[1] != 0xED.toByte()) return@runCatching null
            @Suppress("DEPRECATION")
            BukkitObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as? ItemStack }
        }.getOrNull()?.takeUnless { it.type.isAir } ?: return@ItemTranscoder null
        ConvertedItem(PaperItems.encode(stack), PaperItems.display(stack), PriceKeys.of(PaperFacts.of(stack), house.config().median), stack.amount)
    }

    companion object {
        val SOURCES = listOf("zauctionhouse", "zauctionhouse-v3")
    }
}
