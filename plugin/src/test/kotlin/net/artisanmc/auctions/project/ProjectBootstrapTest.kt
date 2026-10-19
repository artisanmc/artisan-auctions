package net.artisanmc.auctions.project

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ProjectBootstrapTest {
    private val root = Files.createTempDirectory("auc-bootstrap").toFile()
    private val bootstrap = ProjectBootstrap { javaClass.getResourceAsStream("/$it") }

    @AfterTest fun cleanup() { root.deleteRecursively() }

    @Test fun `writes every listed file when the project is absent`() {
        val dir = File(root, "editor")
        assertTrue(bootstrap.writeIfAbsent(dir))
        val index = javaClass.getResource("/bootstrap.index")!!.readText().lines().filter { it.isNotBlank() }
        index.forEach { assertTrue(File(dir, it).isFile, it) }
        assertFalse(File(root, ".editor.tmp").exists())
    }

    @Test fun `never touches an existing project`() {
        val dir = File(root, "editor").apply { mkdirs() }
        File(dir, "manifest.yaml").writeText("mine")
        assertFalse(bootstrap.writeIfAbsent(dir))
        assertEquals("mine", File(dir, "manifest.yaml").readText())
        assertEquals(listOf("manifest.yaml"), dir.list()!!.toList())
    }

    @Test fun `a listed file missing from the jar fails before anything is in place`() {
        val broken = ProjectBootstrap { if (it == ProjectBootstrap.INDEX) "manifest.yaml\nnope.yaml".byteInputStream() else javaClass.getResourceAsStream("/$it") }
        val dir = File(root, "editor")
        assertFails { broken.writeIfAbsent(dir) }
        assertFalse(dir.exists())
    }
}
