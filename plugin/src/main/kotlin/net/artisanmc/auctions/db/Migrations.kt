package net.artisanmc.auctions.db

import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * Applies [Schema.migrations] in order and records each one in
 * `{prefix}schema_history`. Safe to run at every start, and safe after a crash in
 * the middle of a migration: MySQL commits DDL statement by statement, so a
 * migration may be replayed over tables it already created. Tables use
 * `IF NOT EXISTS`; an index that fails to create is accepted when it exists.
 */
class Migrations(
    private val dataSource: DataSource,
    private val logger: Logger,
    /** `api.database.tablePrefix(Schema.MODULE)`, such as `artisan_auctions_`. */
    private val prefix: String,
    private val migrations: List<Schema.Migration> = Schema.migrations(prefix),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Returns the schema version after the run. */
    fun run(): Int = dataSource.connection.use { c ->
        c.createStatement().use {
            it.execute(
                "CREATE TABLE IF NOT EXISTS ${prefix}schema_history (" +
                    "version INT NOT NULL PRIMARY KEY, description VARCHAR(255) NOT NULL, applied_at BIGINT NOT NULL)",
            )
        }
        var current = currentVersion(c)
        val latest = migrations.maxOfOrNull { it.version } ?: 0
        check(current <= latest) {
            "the auctions tables are at version $current, newer than this plugin ($latest): update ArtisanAuctions"
        }
        for (m in migrations.sortedBy { it.version }.filter { it.version > current }) {
            m.statements.forEach { sql -> execute(c, sql.trimIndent()) }
            c.prepareStatement("INSERT INTO ${prefix}schema_history (version, description, applied_at) VALUES (?, ?, ?)").use {
                it.setInt(1, m.version)
                it.setString(2, m.description)
                it.setLong(3, clock())
                it.executeUpdate()
            }
            if (!c.autoCommit) c.commit()
            logger.info("[Auctions] database migrated to version ${m.version} (${m.description})")
            current = m.version
        }
        current
    }

    private fun currentVersion(c: Connection): Int =
        c.createStatement().use { s ->
            s.executeQuery("SELECT MAX(version) FROM ${prefix}schema_history").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    private fun execute(c: Connection, sql: String) {
        try {
            c.createStatement().use { it.execute(sql) }
        } catch (e: SQLException) {
            val index = CREATE_INDEX.find(sql)
            if (index == null || !indexExists(c, index.groupValues[2], index.groupValues[1])) throw e
        }
    }

    private fun indexExists(c: Connection, table: String, index: String): Boolean {
        val meta = c.metaData
        for (t in listOf(table, table.uppercase(), table.lowercase()).distinct()) {
            meta.getIndexInfo(c.catalog, null, t, false, false).use { rs ->
                while (rs.next()) if (rs.getString("INDEX_NAME")?.equals(index, ignoreCase = true) == true) return true
            }
        }
        return false
    }

    private companion object {
        val CREATE_INDEX = Regex("^CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)", RegexOption.IGNORE_CASE)
    }
}
