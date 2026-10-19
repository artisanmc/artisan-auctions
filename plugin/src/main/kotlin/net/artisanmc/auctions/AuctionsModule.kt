package net.artisanmc.auctions

import net.artisanmc.auctions.config.ConfigModel
import net.artisanmc.auctions.config.ConfigStore
import net.artisanmc.auctions.paper.AuctionHouse
import net.artisanmc.auctions.paper.Currencies
import net.artisanmc.auctions.paper.ModuleBindings
import net.artisanmc.auctions.sources.SettingsSource
import net.artisanmc.modules.api.ArtisanAPI
import net.artisanmc.modules.api.ArtisanModule
import java.util.logging.Logger

/**
 * The add-on as Artisan sees it. Everything the editor shows (model, screen,
 * data sources, commands) is declared in [onEnable]: Artisan attributes what is
 * declared there to this module.
 */
class AuctionsModule(
    private val config: ConfigStore,
    private val currencies: Currencies,
    private val logger: Logger,
    private val house: (ArtisanAPI) -> AuctionHouse,
) : ArtisanModule {
    override fun getId(): String = ID
    override fun getSchemaVersion(): String = "auctions/v1"

    private var api: ArtisanAPI? = null

    override fun onEnable(api: ArtisanAPI) {
        this.api = api
        api.models.declare(ConfigModel.declaration())
        api.screens.declare(SETTINGS_SCREEN, "Settings", CONFIG_PATH.removeSuffix(".yaml"), PROJECT_ID)
        api.dataSources.register(SettingsSource.declaration { config.current })
        loadConfig(api)
        ModuleBindings.declare(api, house(api))
    }

    /** After each sync from the editor and `/artisan reload`. */
    override fun onReload() {
        api?.let(::loadConfig)
    }

    override fun onDisable() {
        api = null
    }

    private fun loadConfig(api: ArtisanAPI) {
        if (!config.reload(api.project.read(PROJECT_ID, CONFIG_PATH))) return
        val unknown = config.current.currencies.allowed.filterNot(currencies::has)
        if (unknown.isNotEmpty()) {
            logger.warning("[Auctions] currencies.allowed names currencies no plugin provides: " +
                "${unknown.joinToString()} (known: ${currencies.ids().sorted().joinToString().ifEmpty { "none" }})")
        }
    }

    companion object {
        const val ID = "auctions"
        /** The module's folder, `plugins/Artisan/modules/auctions/`, and the id of its Project. */
        const val PROJECT_ID = "auctions"
        /** Where the Project lived before Artisan 2.8 gave modules their folder: moved from there once. */
        const val LEGACY_PROJECT_DIR = "editor"
        const val CONFIG_PATH = "data/config.yaml"
        const val SETTINGS_SCREEN = "auctions:settings"
    }
}
