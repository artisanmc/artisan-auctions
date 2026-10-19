package net.artisanmc.auctions.files

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes the module's default files (menus, dialogs, settings, translations) into its
 * folder the first time the add-on starts, when the folder is missing or empty (Artisan
 * creates it empty). A folder that holds anything is never touched: from then on the
 * files belong to the admin and the editor. The files go to a sibling `.tmp` directory
 * first and are renamed in one step, so a crash never leaves half the files in a folder
 * that would then count as "already there".
 */
class DefaultFiles(
    private val resource: (String) -> InputStream?,
) {
    /** True when the files were written. */
    fun writeIfEmpty(dir: File): Boolean {
        if (dir.exists() && !dir.list().isNullOrEmpty()) return false
        val index = resource(INDEX)?.bufferedReader()?.use { it.readLines() }
            ?.map(String::trim)?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?: error("$INDEX is missing from the plugin jar")
        val tmp = File(dir.parentFile, ".${dir.name}.tmp")
        tmp.deleteRecursively()
        for (path in index) {
            require(!path.startsWith("/") && ".." !in path.split('/')) { "bad default file path '$path'" }
            val target = File(tmp, path)
            target.parentFile.mkdirs()
            val input = resource("$ROOT/$path") ?: error("default file '$path' is listed but missing")
            input.use { Files.copy(it, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        // An empty folder cannot be renamed over on every system: it goes first.
        if (dir.exists()) Files.delete(dir.toPath())
        Files.move(tmp.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return true
    }

    companion object {
        const val ROOT = "bootstrap"
        const val INDEX = "bootstrap.index"
    }
}
