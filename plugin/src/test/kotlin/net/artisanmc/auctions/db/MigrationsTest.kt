package net.artisanmc.auctions.db

import org.h2.jdbcx.JdbcDataSource
import java.util.logging.Logger
import kotlin.test.*

class MigrationsTest {
    private val ds = JdbcDataSource().apply { setURL("jdbc:h2:mem:auc_${System.nanoTime()};DB_CLOSE_DELAY=-1") }
    private val logger = Logger.getAnonymousLogger()

    private fun tables(): Set<String> = ds.connection.use { c ->
        c.metaData.getTables(null, null, "AUC_%", arrayOf("TABLE")).use { rs ->
            buildSet { while (rs.next()) add(rs.getString("TABLE_NAME").lowercase()) }
        }
    }

    @Test fun `creates every table once and records the version`() {
        assertEquals(1, Migrations(ds, logger).run())
        assertEquals(setOf("auc_listings", "auc_listing_items", "auc_bids", "auc_sales", "auc_claims",
            "auc_import_map", "auc_schema_history"), tables())
        assertEquals(1, Migrations(ds, logger).run(), "a second start changes nothing")
    }

    @Test fun `a migration interrupted after its first statements is replayed`() {
        val first = Schema.migrations.first()
        ds.connection.use { c -> first.statements.take(3).forEach { sql -> c.createStatement().use { it.execute(sql.trimIndent()) } } }
        assertEquals(1, Migrations(ds, logger).run())
        assertTrue("auc_claims" in tables())
    }

    @Test fun `a database newer than the plugin is refused`() {
        Migrations(ds, logger).run()
        ds.connection.use { c ->
            c.createStatement().use { it.execute("INSERT INTO auc_schema_history VALUES (99, 'future', 0)") }
        }
        assertFailsWith<IllegalStateException> { Migrations(ds, logger).run() }
    }

    @Test fun `amounts keep their cents`() {
        Migrations(ds, logger).run()
        ds.connection.use { c ->
            c.createStatement().use {
                it.execute("INSERT INTO auc_sales VALUES ('s', 'l', 'srv', 'sale', 'k', 'vault', 3, 10.01, 3.34, 'a', 'b', 0)")
            }
            c.createStatement().use { s ->
                s.executeQuery("SELECT total, unit_price FROM auc_sales").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("10.01", rs.getBigDecimal(1).toPlainString())
                    assertEquals("3.34", rs.getBigDecimal(2).toPlainString())
                }
            }
        }
    }
}
