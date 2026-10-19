package net.artisanmc.auctions.sources

import net.artisanmc.auctions.config.AuctionsConfig
import net.artisanmc.modules.api.DataSourceRegistry
import kotlin.test.*

class SettingsSourceTest {
    @Test fun `one row that follows the config`() {
        var config = AuctionsConfig()
        val registry = DataSourceRegistry()
        registry.register(SettingsSource.declaration { config })

        val both = registry.fetch(SettingsSource.REF, null, emptyMap()).single()
        assertEquals(true, both["both"])
        assertEquals("1d", both["default_duration"])
        assertEquals(listOf("1h", "6h", "12h", "1d", "2d"), both["durations"])

        config = config.copy(auctions = config.auctions.copy(enabled = false))
        val salesOnly = registry.fetch(SettingsSource.REF, null, emptyMap()).single()
        assertEquals(false, salesOnly["both"])
        assertEquals(false, salesOnly["auctions_enabled"])
    }

    @Test fun `every row field is declared`() {
        val decl = SettingsSource.declaration { AuctionsConfig() }
        assertEquals(SettingsSource.row(AuctionsConfig()).keys, decl.schema.map { it.name }.toSet())
    }
}
