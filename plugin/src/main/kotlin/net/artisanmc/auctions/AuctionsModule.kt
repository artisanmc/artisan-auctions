package net.artisanmc.auctions

import net.artisanmc.auctions.config.ConfigModel
import net.artisanmc.auctions.config.ConfigStore
import net.artisanmc.auctions.files.ModuleFiles
import net.artisanmc.auctions.paper.ArtisanImports
import net.artisanmc.auctions.paper.ArtisanStats
import net.artisanmc.auctions.paper.AuctionHouse
import net.artisanmc.auctions.paper.Imports
import net.artisanmc.auctions.paper.Currencies
import net.artisanmc.auctions.paper.ModuleBindings
import net.artisanmc.auctions.sources.SettingsSource
import net.artisanmc.modules.api.ArtisanAPI
import net.artisanmc.modules.api.ArtisanModule
import net.artisanmc.modules.api.editor.EditorBundleDeclaration
import net.artisanmc.modules.api.playercommand.PlayerCommandDeclaration
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The add-on as Artisan sees it. Everything the editor shows (model, screen,
 * data sources, commands, the folder's files) is declared in [onEnable]: Artisan attributes what is
 * declared there to this module.
 */
class AuctionsModule(
    private val config: ConfigStore,
    private val currencies: Currencies,
    private val logger: Logger,
    private val house: (ArtisanAPI) -> AuctionHouse,
    /** `/ah` and its sub-commands. */
    private val ahCommand: () -> PlayerCommandDeclaration,
    private val imports: Imports,
) : ArtisanModule {
    override fun getId(): String = ID
    override fun getSchemaVersion(): String = "auctions/v1"

    private var api: ArtisanAPI? = null
    /** False until the server has ticked once: a plugin enabled after this one may still register its currency. */
    @Volatile private var started = false

    override fun onEnable(api: ArtisanAPI) {
        this.api = api
        registerFiles(api)
        api.models.declare(ConfigModel.declaration())
        api.screens.declare(SETTINGS_SCREEN, "Settings", CONFIG_PATH.removeSuffix(".yaml"), FOLDER)
        api.dataSources.register(SettingsSource.declaration { config.current })
        loadConfig(api)
        val house = house(api)
        ModuleBindings.declare(api, house)
        statistics(api, house)
        house.threads.main {
            started = true
            warnUnknownCurrencies()
        }
        // Here, not in the plugin's onEnable: `/ah` belongs to this module, its folder holds its
        // file, and disabling the module removes it.
        api.playerCommands.register(ahCommand())
        // `/artisan import auctions:<source>` (Artisan's API 1.7.0); `/ah admin import` otherwise.
        if (api.isCompatibleWith(ArtisanImports.MIN_API)) ArtisanImports.register(api, imports)
    }

    /** After each sync from the editor and `/artisan reload`. */
    override fun onReload() {
        api?.let(::loadConfig)
    }

    override fun onDisable() {
        api = null
    }

    /**
     * Artisan writes the missing ones off the game thread, after this start has read the config:
     * a first start runs on the defaults, which are what the shipped `data/config.yaml` holds.
     */
    private fun registerFiles(api: ArtisanAPI) {
        try {
            api.templates.register(ModuleFiles.template(javaClass.classLoader))
        } catch (e: RuntimeException) {
            logger.log(Level.WARNING, "[Auctions] the house's menus, dialogs and texts are not available", e)
        }
    }

    /**
     * The house's Stat events and the editor's Auctions tab (`editor/`, built into
     * `artisan-editor/ui.js`), both from API [ArtisanStats.MIN_API]: an older Artisan runs the
     * house without them.
     */
    private fun statistics(api: ArtisanAPI, house: AuctionHouse) {
        house.statSink = ArtisanStats.install(api, house, logger) ?: return
        try {
            api.editorBundles.register(EditorBundleDeclaration.fromResource(javaClass.classLoader, EDITOR_BUNDLE, EDITOR_SDK))
        } catch (e: RuntimeException) {
            logger.log(Level.WARNING, "[Auctions] the editor's Auctions tab is not available", e)
        }
    }

    private fun loadConfig(api: ArtisanAPI) {
        if (config.reload(readConfig(api)) && started) warnUnknownCurrencies()
    }

    private fun warnUnknownCurrencies() {
        val unknown = config.current.currencies.allowed.filterNot(currencies::has)
        if (unknown.isNotEmpty()) {
            logger.warning("[Auctions] currencies.allowed names currencies no plugin provides: " +
                "${unknown.joinToString()} (known: ${currencies.ids().sorted().joinToString().ifEmpty { "none" }})")
        }
    }

    /**
     * The owner's `data/config.yaml` when they edited it, else the shipped one: Artisan keeps a
     * module's files under `originals/` and the owner's folder holds only what they changed.
     */
    private fun readConfig(api: ArtisanAPI): String? =
        api.project.read(FOLDER, CONFIG_PATH) ?: api.project.read(FOLDER, ORIGINALS + CONFIG_PATH)

    companion object {
        const val ID = "auctions"
        /** Where Artisan writes the files a module ships, inside its folder. */
        const val ORIGINALS = "originals/"
        /** The folder of the module's files inside this plugin's data folder. */
        const val MODULE_DIR = "artisan_module"
        /** The module's project id, the one Artisan's API reads its files by. */
        const val FOLDER = "auctions"
        /** Where the files lived before Artisan gave modules their folder: moved from there once. */
        const val LEGACY_DIR = "editor"
        const val CONFIG_PATH = "data/config.yaml"
        const val SETTINGS_SCREEN = "auctions:settings"
        /** The editor's Auctions tab, built by `editor/` (`npm run build`). */
        const val EDITOR_BUNDLE = "artisan-editor/ui.js"
        /** The editor SDK `editor/` builds against (the version `npm run build` prints). */
        const val EDITOR_SDK = "1.23.0"
    }
}
