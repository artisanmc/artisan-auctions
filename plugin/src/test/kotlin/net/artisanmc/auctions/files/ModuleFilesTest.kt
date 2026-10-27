package net.artisanmc.auctions.files

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ModuleFilesTest {
    private val root = Files.createTempDirectory("auc-files").toFile()

    @AfterTest fun cleanup() { root.deleteRecursively() }

    @Test fun `the template ships every file of the resources folder, as a system template`() {
        val dir = File(javaClass.getResource("/${ModuleFiles.ROOT}")!!.toURI())
        val shipped = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.sorted().toList()
        assertEquals(shipped, ModuleFiles.PATHS.sorted())

        val template = ModuleFiles.template(javaClass.classLoader)
        assertTrue(template.isSystem)
        assertEquals(ModuleFiles.PATHS.toSet(), template.paths.toSet())
    }

    @Test fun `writes the module file into a folder Artisan already wrote into`() {
        val dir = File(root, "auctions")
        File(dir, ".artisan").mkdirs()
        File(dir, ".artisan/project-id").writeText("x\n")
        File(dir, "commands").mkdirs()
        File(dir, "commands/ah.yml").writeText("schema: command/v1\n")

        assertTrue(ModuleFiles.writeModuleFile(dir))
        assertEquals(ModuleFiles.MODULE_FILE, File(dir, "artisan-module.yml").readText())
        assertFalse(File(dir, ".artisan-module.yml.tmp").exists())
    }

    @Test fun `never replaces a module file, under any of its names`() {
        for (name in listOf("artisan-module.yml", "manifest.yml", "manifest.yaml")) {
            val dir = File(root, name).apply { mkdirs() }
            File(dir, name).writeText("mine")
            assertFalse(ModuleFiles.writeModuleFile(dir), name)
            assertEquals(listOf(name), dir.list()!!.toList())
        }
    }
}
