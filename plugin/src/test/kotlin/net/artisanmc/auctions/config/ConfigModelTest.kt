package net.artisanmc.auctions.config

import org.yaml.snakeyaml.Yaml
import kotlin.test.*

/** The form the editor shows and the file the plugin reads must name the same keys. */
class ConfigModelTest {
    private fun modelKeys(fields: List<*>, prefix: String = ""): Set<String> = fields.flatMap { f ->
        f as Map<*, *>
        val name = prefix + f["name"]
        val type = f["type"] as Map<*, *>
        if (type["kind"] == "object") modelKeys(type["fields"] as List<*>, "$name.") else listOf(name)
    }.toSet()

    /** Keys whose value is a free map (kind `map`): one form field, not a section. */
    private fun mapKeys(fields: List<*>, prefix: String = ""): Set<String> = fields.flatMap { f ->
        f as Map<*, *>
        val name = prefix + f["name"]
        val type = f["type"] as Map<*, *>
        when (type["kind"]) {
            "object" -> mapKeys(type["fields"] as List<*>, "$name.")
            "map" -> listOf(name)
            else -> emptyList()
        }
    }.toSet()

    private fun fileKeys(map: Map<*, *>, leaves: Set<String>, prefix: String = ""): Set<String> = map.flatMap { (k, v) ->
        val name = "$prefix$k"
        if (v is Map<*, *> && name !in leaves) fileKeys(v, leaves, "$name.") else listOf(name)
    }.toSet()

    @Test fun `every key of the shipped config is in the model and back`() {
        val file = Yaml().load<Map<*, *>>(javaClass.getResource("/module/data/config.yaml")!!.readText())
        val doc = ConfigModel.document()
        val fields = doc["fields"] as List<*>
        assertEquals(fileKeys(file, mapKeys(fields)), modelKeys(fields))
    }

    @Test fun `the declaration carries what model v1 requires`() {
        val doc = ConfigModel.document()
        for (k in listOf("schema", "id", "key_field", "fields")) assertNotNull(doc[k], k)
        assertEquals("model/v1", doc["schema"])
        assertEquals("auctions:config", doc["id"])
    }

    @Test fun `model defaults match the parser defaults`() {
        fun defaults(fields: List<*>): Map<String, Any?> = fields.associate { f ->
            f as Map<*, *>
            val type = f["type"] as Map<*, *>
            f["name"].toString() to (if (type["kind"] == "object") defaults(type["fields"] as List<*>) else f["default"])
        }
        val result = ConfigParser.parse(defaults(ConfigModel.document()["fields"] as List<*>))
        assertEquals(emptyList(), result.errors)
        assertEquals(AuctionsConfig().sales, result.config.sales)
        assertEquals(AuctionsConfig().auctions, result.config.auctions)
        assertEquals(AuctionsConfig().limits, result.config.limits)
        assertEquals(AuctionsConfig().currencies, result.config.currencies)
    }
}
