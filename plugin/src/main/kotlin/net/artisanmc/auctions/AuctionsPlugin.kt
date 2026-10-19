package net.artisanmc.auctions

import net.artisanmc.auctions.api.AuctionsService
import net.artisanmc.auctions.config.ConfigStore
import net.artisanmc.auctions.db.Migrations
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

        api.modules.register(AuctionsModule(configStore, logger))
        server.servicesManager.register(AuctionsService::class.java, AuctionsServiceImpl(configStore), this, ServicePriority.Normal)
    }

    override fun onDisable() {
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
    }
}
