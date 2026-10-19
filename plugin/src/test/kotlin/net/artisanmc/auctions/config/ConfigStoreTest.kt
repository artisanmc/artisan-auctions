package net.artisanmc.auctions.config

import java.util.logging.Logger
import kotlin.test.*

class ConfigStoreTest {
    private val store = ConfigStore(Logger.getAnonymousLogger())

    @Test fun `a broken file keeps the previous settings`() {
        assertTrue(store.reload("sales: { enabled: false }"))
        assertFalse(store.current.sales.enabled)
        assertFalse(store.reload("sales: { duration: soon }"))
        assertFalse(store.reload("sales: [unclosed"))
        assertFalse(store.reload("- a list"))
        assertFalse(store.current.sales.enabled, "still the last valid file")
    }

    @Test fun `a missing file falls back to the defaults`() {
        store.reload("sales: { enabled: false }")
        assertTrue(store.reload(null))
        assertTrue(store.current.sales.enabled)
    }
}
