package net.artisanmc.auctions.project

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes the default Project (menus, dialogs, config, translations) the first
 * time the add-on starts. Never touches an existing directory: from then on the
 * Project belongs to the admin and the editor. The files go to a sibling `.tmp`
 * directory first and are renamed in one step, so a crash never leaves half a
 * Project that would then count as "already there".
 */
class ProjectBootstrap(
    private val resource: (String) -> InputStream?,
) {
    /** True when the Project was written. */
    fun writeIfAbsent(dir: File): Boolean {
        if (dir.exists()) return false
        val index = resource(INDEX)?.bufferedReader()?.use { it.readLines() }
            ?.map(String::trim)?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?: error("$INDEX is missing from the plugin jar")
        val tmp = File(dir.parentFile, ".${dir.name}.tmp")
        tmp.deleteRecursively()
        for (path in index) {
            require(!path.startsWith("/") && ".." !in path.split('/')) { "bad bootstrap path '$path'" }
            val target = File(tmp, path)
            target.parentFile.mkdirs()
            val input = resource("$ROOT/$path") ?: error("bootstrap file '$path' is listed but missing")
            input.use { Files.copy(it, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        Files.move(tmp.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return true
    }

    companion object {
        const val ROOT = "bootstrap"
        const val INDEX = "bootstrap.index"
    }
}
