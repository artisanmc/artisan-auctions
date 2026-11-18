package net.artisanmc.auctions.paper

import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.importer.AuctionHouseReader
import net.artisanmc.auctions.importer.AuctionSource
import net.artisanmc.auctions.importer.CanonicalJson
import net.artisanmc.auctions.importer.Snbt
import net.artisanmc.auctions.importer.ConvertedItem
import net.artisanmc.auctions.importer.Importer
import net.artisanmc.auctions.importer.ItemTranscoder
import net.artisanmc.auctions.importer.ZahV3Reader
import net.artisanmc.auctions.importer.ZahV4Reader
import net.artisanmc.auctions.median.PriceKeys
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

/**
 * The add-on's import sources (ADR `data-imports` of Artisan): zAuctionHouse v4 and v3,
 * AuctionHouse (Kiranhart) and the canonical `auctions/1` file. Each reads its source
 * read-only, from that plugin's own settings. With Artisan's API 1.7.0 they are run by
 * `/artisan import auctions:<source>` ([ArtisanImports]), which logs each run and can roll a batch
 * back; `/ah admin import` hands over to it, and runs them itself on an older Artisan.
 */
class Imports(
    private val plugin: Plugin,
    private val house: AuctionHouse,
    private val economy: Currencies,
    /** True when Artisan runs the imports (`/artisan import`). */
    private val artisanRuns: () -> Boolean = { false },
) {
    private val plugins = plugin.dataFolder.parentFile
    private val running = AtomicBoolean(false)

    /** `/ah admin import <source> [confirm]`. */
    fun run(sender: CommandSender, which: String, confirm: Boolean) {
        if (which !in SOURCES) return sender.sendMessage("/ah admin import <${SOURCES.joinToString("|")}> [confirm]")
        if (artisanRuns()) {
            Bukkit.dispatchCommand(sender, "artisan import auctions:$which${if (confirm) " confirm" else ""}")
            return
        }
        CONFLICTS[which]?.let { names ->
            Bukkit.getPluginManager().plugins.firstOrNull { p -> p.isEnabled && names.any { p.name.startsWith(it, ignoreCase = true) } }?.let {
                return sender.sendMessage("${it.name} is running: remove it and restart first, or items could be taken on both sides.")
            }
        }
        sender.sendMessage("${if (confirm) "Importing" else "Dry run of"} $which…")
        house.threads.async {
            val lines = try {
                val report = execute(which, null, dryRun = !confirm, batch = null)
                report.lines() + when {
                    report.blocked -> listOf("Nothing was imported.")
                    report.dryRun -> listOf("Run '/ah admin import $which confirm' to import.")
                    else -> listOf("Done.")
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "[Auctions] import from $which failed", e)
                listOf("Import failed: ${e.message}. Records written so far are kept; running it again resumes.")
            }
            house.threads.main { lines.forEach(sender::sendMessage) }
        }
    }

    /**
     * Reads [which] (with [file] for `json`), converts and, unless [dryRun], writes under [batch].
     * Blocking, one at a time. The house's cache follows what was written.
     */
    fun execute(which: String, file: File?, dryRun: Boolean, batch: String?): Importer.Report {
        if (!running.compareAndSet(false, true)) error("an import is already running")
        try {
            val (ledger, source) = source(which, file)
            val importer = Importer(house.server, house.store, transcoder(), ::currency)
            val report = importer.run(ledger, source.read(), dryRun = dryRun, batch = batch)
            if (!report.dryRun) refresh()
            return report
        } finally {
            running.set(false)
        }
    }

    class Rollback(val undone: List<String>, val changed: List<String>, val gone: List<String>)

    /** Undoes what [batch] imported and is untouched since; [dryRun] only counts. Blocking. */
    fun rollback(batch: String, dryRun: Boolean): Rollback {
        if (!running.compareAndSet(false, true)) error("an import is already running")
        try {
            val undone = mutableListOf<String>(); val changed = mutableListOf<String>(); val gone = mutableListOf<String>()
            for ((sourceId, target) in house.store.importedInBatch(batch)) {
                when (house.store.undoImport(batch, sourceId, target, dryRun)) {
                    AuctionStore.Undo.UNDONE -> undone += sourceId
                    AuctionStore.Undo.CHANGED -> changed += sourceId
                    AuctionStore.Undo.GONE -> gone += sourceId
                }
            }
            if (!dryRun && undone.isNotEmpty()) refresh()
            return Rollback(undone, changed, gone)
        } finally {
            running.set(false)
        }
    }

    private fun refresh() {
        val active = house.store.activeListings(house.server)
        house.threads.main {
            house.cache.replaceAll(active)
            house.medians.clear()
            house.scheduleRefresh()
        }
    }

    /** Source name → currency id: the configured map, else the same name if it is registered. */
    private fun currency(name: String): String? {
        house.config().import.currencyMap.entries.firstOrNull { it.key.equals(name, true) }?.let { return it.value.takeIf(economy::has) }
        return listOf(name, name.lowercase()).firstOrNull(economy::has)
    }

    private fun defaultEnd(): Long = System.currentTimeMillis() + house.config().sales.duration.toMillis()

    /** The ledger's name for [which] and its reader. */
    private fun source(which: String, file: File?): Pair<String, AuctionSource> {
        val zah = File(plugins, "zAuctionHouse")
        val config by lazy { yaml(File(zah, "config.yml")) }
        return when (which) {
            "zauctionhouse" -> {
                // A database-configuration.yml in the server folder overrides the plugin's own.
                val db = (yaml(File("database-configuration.yml")).takeIf { it.isNotEmpty() }
                    ?: (config["database-configuration"] as? Map<*, *>).orEmpty())
                val prefix = db["table-prefix"]?.toString() ?: "zauctionhousev4_"
                val type = config["storage-type"]?.toString()?.uppercase() ?: "SQLITE"
                which to ZahV4Reader(connector(type, File(zah, "database.db"), db), prefix, ::defaultEnd)
            }
            "zauctionhouse-v3" -> {
                val v3 = ((config["migration"] as? Map<*, *>)?.get("zauctionhouse-v3") as? Map<*, *>).orEmpty()
                val folder = v3["json-folder"]?.toString()?.let(::File)
                    ?: listOf("zAuctionHouseV3", "zAuctionHousev3").map { File(plugins, it) }.firstOrNull { it.isDirectory }
                    ?: File(plugins, "zAuctionHouseV3")
                val sqlite = v3["sqlite-path"]?.toString()?.let(::File) ?: File(folder, "database.db")
                val type = v3["source-type"]?.toString()?.uppercase() ?: if (sqlite.isFile) "SQLITE" else "JSON"
                "zauctionhouse-v3" to (if (type == "JSON") ZahV3Reader.json(folder, ::defaultEnd)
                else ZahV3Reader.sql(connector(type, sqlite, v3), v3["table-prefix"]?.toString() ?: "zauctionhouse_", ::defaultEnd))
            }
            "auctionhouse" -> {
                val folder = File(plugins, "AuctionHouse")
                val db = (yaml(File(folder, "config.yml"))["database"] as? Map<*, *>).orEmpty()
                val useDb = db["use database"]?.toString().toBoolean()
                val mysql = mapOf("host" to db["host"], "port" to db["port"], "database" to db["name"], "user" to db["username"], "password" to db["password"])
                which to AuctionHouseReader(
                    connector(if (useDb) "MYSQL" else "SQLITE", File(folder, "auctionhouse.db"), mysql),
                    if (useDb) db["table prefix"]?.toString() ?: "auctionhouse_" else "auctionhouse_",
                )
            }
            "json" -> {
                val f = requireNotNull(file) { "the json source reads a file: /artisan import auctions:json <file>" }
                val doc = f.reader(Charsets.UTF_8).use(CanonicalJson::read)
                "json:${doc.source}" to object : AuctionSource {
                    override val id = "json:${doc.source}"
                    override fun read() = doc.data
                }
            }
            else -> error("unknown source $which")
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
            // The server ships the driver in a class loader DriverManager did not scan at startup.
            runCatching { Class.forName("com.mysql.cj.jdbc.Driver") }
            DriverManager.getConnection(url, db["user"]?.toString(), db["password"]?.toString()).apply { isReadOnly = true }
        })
        else -> error("unsupported storage type $type")
    }

    private fun yaml(f: File): Map<*, *> = if (!f.isFile) emptyMap<Any, Any>() else
        runCatching { Yaml(SafeConstructor(LoaderOptions())).load<Any?>(f.readText()) as? Map<*, *> }.getOrNull().orEmpty()

    /** An item read according to its [SourceItem.encoding]; null when it cannot be read here. */
    private fun transcoder() = ItemTranscoder { item ->
        val stack = runCatching {
            when (item.encoding) {
                "zah" -> zah(item.raw)
                "bukkit_yaml" -> YamlConfiguration().apply { loadFromString(item.raw) }.let { y ->
                    y.getItemStack("i") ?: y.getKeys(false).firstNotNullOfOrNull(y::getItemStack)
                }
                "paper" -> PaperItems.decodeOrNull(item.raw)
                "snbt" -> if (!Platform.paper) null else {
                    @Suppress("DEPRECATION") val version = Bukkit.getUnsafe().dataVersion
                    PaperItems.decodeOrNull(Base64.getEncoder().encodeToString(Snbt.toPaperBytes(item.raw, version)))
                }
                else -> null
            }
        }.getOrNull()?.takeUnless { it.type.isAir } ?: return@ItemTranscoder null
        ConvertedItem(PaperItems.encode(stack), PaperItems.display(stack), PriceKeys.of(PaperFacts.of(stack), house.config().median), stack.amount)
    }

    /**
     * zAuctionHouse stores Base64 of a GZIP'd Bukkit object stream (1.20.5+), optionally
     * marked `V2:`. The older NBT form (marked `NBT:` or not a Java stream) is not read:
     * those records stay in the source and are listed.
     */
    private fun zah(text: String): ItemStack? {
        val raw = text.removePrefix("V2:")
        if (raw.startsWith("NBT:")) return null
        val bytes = GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(raw.trim()))).use { it.readBytes() }
        if (bytes.size < 2 || bytes[0] != 0xAC.toByte() || bytes[1] != 0xED.toByte()) return null
        @Suppress("DEPRECATION")
        return BukkitObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as? ItemStack }
    }

    companion object {
        /** The sources `/ah admin import` offers; `json` needs a file, so only `/artisan import`. */
        val SOURCES = listOf("zauctionhouse", "zauctionhouse-v3", "auctionhouse")
        /** Plugins that must not run while a source is imported: they could hand the same items out. */
        val CONFLICTS = mapOf(
            "zauctionhouse" to listOf("zAuctionHouse"),
            "zauctionhouse-v3" to listOf("zAuctionHouse"),
            "auctionhouse" to listOf("AuctionHouse"),
            "json" to emptyList(),
        )
    }
}
