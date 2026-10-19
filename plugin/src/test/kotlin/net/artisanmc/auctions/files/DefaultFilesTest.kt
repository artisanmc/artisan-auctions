package net.artisanmc.auctions.files

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DefaultFilesTest {
    private val root = Files.createTempDirectory("auc-defaults").toFile()
    private val defaults = DefaultFiles { javaClass.getResourceAsStream("/$it") }
    private val index = javaClass.getResource("/bootstrap.index")!!.readText().lines().filter { it.isNotBlank() }

    @AfterTest fun cleanup() { root.deleteRecursively() }

    @Test fun `writes every listed file when the folder is absent`() {
        val dir = File(root, "auctions")
        assertTrue(defaults.writeIfEmpty(dir))
        index.forEach { assertTrue(File(dir, it).isFile, it) }
        assertFalse(File(root, ".auctions.tmp").exists())
    }

    @Test fun `writes them into the empty folder Artisan created`() {
        val dir = File(root, "auctions").apply { mkdirs() }
        assertTrue(defaults.writeIfEmpty(dir))
        index.forEach { assertTrue(File(dir, it).isFile, it) }
    }

    @Test fun `never touches a folder that holds files`() {
        val dir = File(root, "auctions").apply { mkdirs() }
        File(dir, "manifest.yaml").writeText("mine")
        assertFalse(defaults.writeIfEmpty(dir))
        assertEquals("mine", File(dir, "manifest.yaml").readText())
        assertEquals(listOf("manifest.yaml"), dir.list()!!.toList())
    }

    @Test fun `a listed file missing from the jar fails before anything is in place`() {
        val broken = DefaultFiles { if (it == DefaultFiles.INDEX) "manifest.yaml\nnope.yaml".byteInputStream() else javaClass.getResourceAsStream("/$it") }
        val dir = File(root, "auctions")
        assertFails { broken.writeIfEmpty(dir) }
        assertFalse(dir.exists())
    }
}
