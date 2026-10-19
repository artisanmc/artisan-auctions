package net.artisanmc.auctions.db

import net.artisanmc.auctions.P
import org.h2.jdbcx.JdbcDataSource
import java.util.logging.Logger
import kotlin.test.*

class MigrationsTest {
    private val ds = JdbcDataSource().apply { setURL("jdbc:h2:mem:auc_${System.nanoTime()};DB_CLOSE_DELAY=-1") }
    private val logger = Logger.getAnonymousLogger()

    private fun tables(): Set<String> = ds.connection.use { c ->
        c.metaData.getTables(null, null, "ARTISAN_AUCTIONS_%", arrayOf("TABLE")).use { rs ->
            buildSet { while (rs.next()) add(rs.getString("TABLE_NAME").lowercase()) }
        }
    }

    @Test fun `creates every table once and records the version`() {
        val latest = Schema.migrations(P).maxOf { it.version }
        assertEquals(latest, Migrations(ds, logger, P).run())
        assertEquals(setOf("listings", "listing_items", "bids", "sales", "claims",
            "import_map", "payments", "schema_history").map { P + it }.toSet(), tables())
        assertEquals(latest, Migrations(ds, logger, P).run(), "a second start changes nothing")
    }

    @Test fun `a migration interrupted after its first statements is replayed`() {
        val first = Schema.migrations(P).first()
        ds.connection.use { c -> first.statements.take(3).forEach { sql -> c.createStatement().use { it.execute(sql.trimIndent()) } } }
        assertEquals(Schema.migrations(P).maxOf { it.version }, Migrations(ds, logger, P).run())
        assertTrue(P + "claims" in tables())
    }

    @Test fun `a database newer than the plugin is refused`() {
        Migrations(ds, logger, P).run()
        ds.connection.use { c ->
            c.createStatement().use { it.execute("INSERT INTO ${P}schema_history VALUES (99, 'future', 0)") }
        }
        assertFailsWith<IllegalStateException> { Migrations(ds, logger, P).run() }
    }

    @Test fun `another prefix keeps its own tables`() {
        Migrations(ds, logger, P).run()
        Migrations(ds, logger, "srv2_auctions_").run()
        val other = ds.connection.use { c ->
            c.metaData.getTables(null, null, "SRV2_AUCTIONS_%", arrayOf("TABLE")).use { rs -> buildSet { while (rs.next()) add(rs.getString("TABLE_NAME").lowercase()) } }
        }
        assertEquals(tables().map { it.removePrefix(P) }.toSet(), other.map { it.removePrefix("srv2_auctions_") }.toSet())
    }

    @Test fun `amounts keep their cents`() {
        Migrations(ds, logger, P).run()
        ds.connection.use { c ->
            c.createStatement().use {
                it.execute("INSERT INTO ${P}sales VALUES ('s', 'l', 'srv', 'sale', 'k', 'vault', 3, 10.01, 3.34, 'a', 'b', 0)")
            }
            c.createStatement().use { s ->
                s.executeQuery("SELECT total, unit_price FROM ${P}sales").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("10.01", rs.getBigDecimal(1).toPlainString())
                    assertEquals(0, java.math.BigDecimal("3.34").compareTo(rs.getBigDecimal(2)))
                }
            }
        }
    }
}
