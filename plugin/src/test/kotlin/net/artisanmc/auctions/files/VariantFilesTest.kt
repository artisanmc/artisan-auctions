package net.artisanmc.auctions.files

import org.yaml.snakeyaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The menus' variants the module ships: `textured` (resource pack) and `bedrock`. */
class VariantFilesTest {
    private val otherClick = Regex("^\\s*(right|shift_left|shift_right|middle|drop)\\s*:", RegexOption.MULTILINE)

    /**
     * A variant's menu takes the inputs of the module's menu (Artisan leaves it out otherwise, and
     * its players silently get the module's), and the Bedrock versions only use the left click,
     * the only one Bedrock sends.
     */
    @Test fun `variants keep the inputs and Bedrock only taps`() {
        val files = ModuleFiles.template(javaClass.classLoader)
        val variants = files.paths.filter { it.startsWith("variants/") }
        for (path in variants) {
            val (_, name, rest) = path.split("/", limit = 3)
            val text = String(files.getFile(path)!!, Charsets.UTF_8)
            val variant = Yaml().load<Map<String, Any?>>(text)
            val module = Yaml().load<Map<String, Any?>>(String(files.getFile(rest)!!, Charsets.UTF_8))
            assertEquals(module["inputs"], variant["inputs"], path)
            if (name == "bedrock") assertFalse(otherClick.containsMatchIn(text), path)
        }
        assertEquals(8, variants.size)
    }
}
