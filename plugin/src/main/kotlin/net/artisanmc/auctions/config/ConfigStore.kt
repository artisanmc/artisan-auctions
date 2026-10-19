package net.artisanmc.auctions.config

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.util.logging.Logger

/**
 * The config in force. [reload] swaps it only when the file reads cleanly; an
 * unreadable file keeps the previous config and logs why, so a typo never
 * changes the rules mid-game.
 */
class ConfigStore(private val logger: Logger) {
    @Volatile var current: AuctionsConfig = AuctionsConfig()
        private set

    /** [yaml] = the content of `data/config.yaml`, null when the file is missing. True when applied. */
    fun reload(yaml: String?): Boolean {
        if (yaml == null) {
            logger.warning("[Auctions] data/config.yaml is missing: using the defaults")
            current = AuctionsConfig()
            return true
        }
        val root = try {
            Yaml(SafeConstructor(LoaderOptions())).load<Any?>(yaml)
        } catch (e: Exception) {
            logger.warning("[Auctions] data/config.yaml is not valid YAML, previous settings kept: ${e.message}")
            return false
        }
        if (root != null && root !is Map<*, *>) {
            logger.warning("[Auctions] data/config.yaml must be a mapping, previous settings kept")
            return false
        }
        val result = ConfigParser.parse(root as Map<*, *>?)
        if (result.errors.isNotEmpty()) {
            logger.warning("[Auctions] data/config.yaml has errors, previous settings kept:\n  - " +
                result.errors.joinToString("\n  - "))
            return false
        }
        current = result.config
        return true
    }
}
