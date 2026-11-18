package net.artisanmc.auctions.files

import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.*

/** Every message the code sends exists in each language of the default files. */
class MessagesTest {
    private fun messages(lang: String): Set<String> {
        val tree = Yaml().load<Map<String, Any?>>(javaClass.getResource("/module/lang/$lang.yaml")!!.readText())
        @Suppress("UNCHECKED_CAST")
        return ((tree["auctions"] as Map<String, Any?>)["messages"] as Map<String, Any?>).keys
    }

    private val used: Set<String> by lazy {
        val patterns = listOf(Regex("""tell\([^,()]+, "([a-z_]+)""""), Regex("""Problem\("([a-z_]+)""""))
        File("src/main/kotlin").walk().filter { it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            patterns.asSequence().flatMap { p -> p.findAll(text).map { it.groupValues[1] } }
        }.toSet() + setOf("vetoed", "bought", "listed", "cancelled")
    }

    @Test fun `the sources were found`() = assertTrue(used.size > 20, "found $used")

    @Test fun `english has every message`() = assertEquals(emptySet(), used - messages("en"))

    @Test fun `french has every message`() = assertEquals(emptySet(), used - messages("fr"))

    @Test fun `german has every message`() = assertEquals(emptySet(), used - messages("de"))

    @Test fun `spanish has every message`() = assertEquals(emptySet(), used - messages("es"))

    @Test fun `languages have the same keys`() {
        for (lang in listOf("fr", "de", "es")) assertEquals(messages("en"), messages(lang), lang)
    }

    /** Every `{ en: …, fr: … }` text of a menu or dialog speaks the four languages. */
    @Test fun `menus and dialogs speak every language`() {
        val missing = mutableListOf<String>()
        fun walk(node: Any?, where: String) {
            when (node) {
                is Map<*, *> -> {
                    if ("en" in node.keys && node.keys != setOf("en", "fr", "de", "es")) missing += "$where: ${node.keys}"
                    node.forEach { (k, v) -> walk(v, "$where.$k") }
                }
                is List<*> -> node.forEachIndexed { i, v -> walk(v, "$where[$i]") }
            }
        }
        File("src/main/resources/module").walk().filter { it.extension == "yaml" && "/lang/" !in it.invariantSeparatorsPath }
            .forEach { walk(Yaml().load<Any?>(it.readText()), it.name) }
        assertEquals(emptyList<String>(), missing)
    }
}
