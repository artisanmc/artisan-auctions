package net.artisanmc.auctions.files

import net.artisanmc.modules.api.template.TemplateDeclaration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The files the add-on ships for its folder `plugins/Artisan/modules/auctions/`: menus, dialogs,
 * texts and settings, a system template Artisan keeps there (`TemplateDeclaration.system()`). A
 * missing file is written again, an untouched one follows each new version, an edited one stays
 * the admin's until Reset in the editor, and a file already on disk before (written by an
 * earlier version) counts as edited. Artisan writes them under `.yml`, whatever [ROOT] names them.
 *
 * Artisan writes `.artisan/` and the command files into the folder before the add-on starts: a
 * folder is never empty on a first start, which is why "write the defaults into an empty folder"
 * left Artisan 2 servers with `/ah` alone.
 */
object ModuleFiles {
    const val ROOT = "module"
    const val TEMPLATE = "auctions_files"
    /** Raise it whenever a file of [PATHS] changes: untouched copies follow, edited ones merge. */
    const val VERSION = 1

    val PATHS = listOf(
        "data/config.yaml",
        "dialogs/ah_bid.yaml",
        "dialogs/ah_confirm_buy.yaml",
        "dialogs/ah_price_warning.yaml",
        "dialogs/ah_search.yaml",
        "dialogs/ah_sell_auction.yaml",
        "dialogs/ah_sell_fixed.yaml",
        "dialogs/ah_sell_type.yaml",
        "lang/en.yaml",
        "lang/fr.yaml",
        "menus/ah_claims.yaml",
        "menus/ah_lot.yaml",
        "menus/ah_main.yaml",
        "menus/ah_mine.yaml",
        "models/config.yaml",
    )

    fun template(loader: ClassLoader): TemplateDeclaration =
        TemplateDeclaration.fromResources(loader, ROOT, TEMPLATE, VERSION, PATHS)
            .title("Auction house")
            .system()
            .build()

    /** The module file under each name Artisan reads, the first one it looks for first. */
    private val MODULE_FILE_NAMES = listOf("artisan-module.yml", "manifest.yml", "manifest.yaml")

    const val MODULE_FILE = """schema: module/v1
default_lang: en
languages:
  - en
  - fr
lore_max_width: 240
"""

    /**
     * The folder's languages and lore width (`artisan-module.yml`), which a template never
     * ships: written when the folder has no module file under any name. True when written.
     */
    fun writeModuleFile(dir: File): Boolean {
        if (MODULE_FILE_NAMES.any { File(dir, it).exists() }) return false
        dir.mkdirs()
        val target = File(dir, MODULE_FILE_NAMES.first())
        val tmp = File(dir, ".${target.name}.tmp")
        tmp.writeText(MODULE_FILE, Charsets.UTF_8)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return true
    }
}
