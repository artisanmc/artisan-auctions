package net.artisanmc.auctions

import net.artisanmc.auctions.api.AuctionsService
import net.artisanmc.auctions.config.ConfigStore
import net.artisanmc.auctions.db.AuctionStore
import net.artisanmc.auctions.db.Migrations
import net.artisanmc.auctions.paper.AdminCommands
import net.artisanmc.auctions.paper.AhCommand
import net.artisanmc.auctions.paper.AuctionHouse
import net.artisanmc.auctions.paper.PlayerListener
import net.artisanmc.auctions.project.ProjectBootstrap
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
        if (api == null || !hasRequiredApi(api)) {
            logger.severe("ArtisanAuctions needs Artisan with the API $MIN_CORE_API or newer (currencies and menu refresh)")
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

        val projectDir = File(dataFolder, "editor")
        api.projectRoots.register(AuctionsModule.PROJECT_ID, projectDir, AuctionsModule.ID)
        if (ProjectBootstrap(::getResource).writeIfAbsent(projectDir)) {
            // The core read its roots before this plugin started.
            api.project.reload()
            logger.info("Default auction house Project written to ${projectDir.path}")
        }

        val house = AuctionHouse(this, api, serverId, AuctionStore(dataSource)) { configStore.current }
        this.house = house
        api.modules.register(AuctionsModule(configStore, logger) { house })
        server.servicesManager.register(AuctionsService::class.java, AuctionsServiceImpl(configStore), this, ServicePriority.Normal)

        val admin = AdminCommands(house)
        getCommand("ah")?.let { cmd -> AhCommand(house, admin).also { cmd.setExecutor(it); cmd.tabCompleter = it } }
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
     * `coreVersion` is the plugin build number, not the API's SemVer: the members
     * this add-on needs are probed instead. An older core has no such getters.
     */
    private fun hasRequiredApi(api: ArtisanAPI): Boolean = try {
        api.economy
        api.menus
        true
    } catch (e: NoSuchMethodError) {
        false
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
        /** First API with api.economy and api.menus. */
        const val MIN_CORE_API = "1.8.0"
        /** Ends due listings every 5 s. */
        const val EXPIRY_TICKS = 100L
        /** Purges claims past their retention every hour. */
        const val PURGE_TICKS = 72_000L
    }
}
