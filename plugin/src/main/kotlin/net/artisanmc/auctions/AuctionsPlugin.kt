package net.artisanmc.auctions

import net.artisanmc.auctions.api.AuctionsService
import net.artisanmc.auctions.config.ConfigStore
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.db.Migrations
import net.artisanmc.auctions.paper.AdminCommands
import net.artisanmc.auctions.paper.AhCommand
import net.artisanmc.auctions.paper.AuctionHouse
import net.artisanmc.auctions.paper.Currencies
import net.artisanmc.auctions.paper.Imports
import net.artisanmc.auctions.paper.PlayerListener
import net.artisanmc.auctions.files.DefaultFiles
import net.artisanmc.modules.api.ArtisanAPI
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import javax.sql.DataSource

class AuctionsPlugin : JavaPlugin() {

    lateinit var serverId: String
        private set
    lateinit var dataSource: DataSource
        private set
    private val configStore by lazy { ConfigStore(logger) }
    private var house: AuctionHouse? = null

    override fun onEnable() {
        val api = server.servicesManager.getRegistration(ArtisanAPI::class.java)?.provider
        if (api == null || !api.isCompatibleWith(MIN_CORE_API)) {
            logger.severe("ArtisanAuctions needs Artisan with the API $MIN_CORE_API or newer (found: ${api?.apiVersion ?: "no Artisan"})")
            server.pluginManager.disablePlugin(this)
            return
        }
        serverId = loadServerId()

        try {
            dataSource = api.database.dataSource(this)
            Migrations(dataSource, logger).run()
        } catch (e: Exception) {
            logger.severe("Database unavailable, ArtisanAuctions is disabled: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        // plugins/Artisan/modules/auctions/: menus, dialogs, texts and settings, edited in the editor.
        val legacy = File(dataFolder, AuctionsModule.LEGACY_DIR).takeIf { it.isDirectory }
        val folder = api.projectRoots.moduleFolder(AuctionsModule.FOLDER, AuctionsModule.ID, legacy)
        if (DefaultFiles(::getResource).writeIfEmpty(folder)) {
            api.project.reload()
            logger.info("Default auction house files written to ${folder.path}")
        }

        val currencies = Currencies(server.servicesManager)
        val house = AuctionHouse(this, api, serverId, AuctionStore(dataSource), currencies) { configStore.current }
        this.house = house
        val admin = AdminCommands(house, Imports(this, house, currencies))
        api.modules.register(AuctionsModule(configStore, currencies, logger) { house })
        // Outside the module's onEnable: owned by this plugin, removed when it disables.
        api.playerCommands.register(AhCommand.declaration(house, admin))
        server.servicesManager.register(AuctionsService::class.java, AuctionsServiceImpl(configStore, house), this, ServicePriority.Normal)

        server.pluginManager.registerEvents(PlayerListener(house), this)
        house.start()
        server.scheduler.runTaskTimer(this, Runnable { house.tick() }, EXPIRY_TICKS, EXPIRY_TICKS)
        server.scheduler.runTaskTimer(this, Runnable { house.purgeStaleClaims() }, PURGE_TICKS, PURGE_TICKS)
    }

    override fun onDisable() {
        server.scheduler.cancelTasks(this)
        house?.stop()
        house = null
        server.servicesManager.unregisterAll(this)
    }

    /**
     * Rows are tagged with this id so two servers sharing the suite's database
     * keep separate houses. Generated once, kept in `config.yml`.
     */
    private fun loadServerId(): String {
        saveDefaultConfig()
        val current = config.getString("server-id").orEmpty().trim()
        if (current.isNotEmpty()) return current
        val generated = "srv-" + UUID.randomUUID().toString().take(8)
        config.set("server-id", generated)
        saveConfig()
        return generated
    }

    companion object {
        /** First API with module folders (`projectRoots.moduleFolder`); player commands since 2.7.0. */
        const val MIN_CORE_API = "2.8.0"
        /** Ends due listings every 5 s. */
        const val EXPIRY_TICKS = 100L
        /** Purges claims past their retention every hour. */
        const val PURGE_TICKS = 72_000L
    }
}
