package net.artisanmc.auctions.importer

import net.artisanmc.auctions.market.ClaimReason
import org.h2.jdbcx.JdbcDataSource
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class ZAuctionHouseTest {
    private val ds = JdbcDataSource().apply { setURL("jdbc:h2:mem:zah_${System.nanoTime()};DB_CLOSE_DELAY=-1") }
    private val seller = UUID.randomUUID()
    private val buyer = UUID.randomUUID()

    private fun exec(vararg sql: String) = ds.connection.use { c -> c.createStatement().use { s -> sql.forEach(s::execute) } }

    @Test fun `v4 tables become listings, claims and sales`() {
        val p = "zauctionhousev4_"
        exec(
            "CREATE TABLE ${p}players (unique_id VARCHAR(36), name VARCHAR(16))",
            "CREATE TABLE ${p}items (id INT, item_type VARCHAR(255), seller_unique_id VARCHAR(36), buyer_unique_id VARCHAR(36), " +
                "price DECIMAL(65,2), economy_name VARCHAR(255), storage_type VARCHAR(16), server_name VARCHAR(255), " +
                "expired_at TIMESTAMP, pending_publish INT, created_at TIMESTAMP, updated_at TIMESTAMP)",
            "CREATE TABLE ${p}auction_items (id INT, item_id INT, itemstack CLOB)",
            "CREATE TABLE ${p}transactions (id INT, item_id INT, player_unique_id VARCHAR(36), economy_name VARCHAR(255), " +
                "\"before\" DECIMAL(65,2), \"after\" DECIMAL(65,2), \"value\" DECIMAL(65,2), status VARCHAR(32), created_at TIMESTAMP)",
            "CREATE TABLE ${p}logs (id INT, item_id INT, log_type VARCHAR(255), player_unique_id VARCHAR(36), target_unique_id VARCHAR(36), " +
                "itemstack CLOB, price DECIMAL(65,2), economy_name VARCHAR(255), created_at TIMESTAMP)",
            "INSERT INTO ${p}players VALUES ('$seller', 'Seller')",
            "INSERT INTO ${p}items VALUES (1, 'AUCTION', '$seller', NULL, 10.50, 'vault', 'LISTED', 's', TIMESTAMP '2030-01-01 00:00:00', 0, TIMESTAMP '2026-01-01 00:00:00', NULL)",
            "INSERT INTO ${p}items VALUES (2, 'AUCTION', '$seller', '$buyer', 5, 'vault', 'PURCHASED', 's', NULL, 0, NULL, NULL)",
            "INSERT INTO ${p}items VALUES (3, 'AUCTION', '$seller', NULL, 5, 'vault', 'EXPIRED', 's', NULL, 0, NULL, NULL)",
            "INSERT INTO ${p}items VALUES (4, 'AUCTION', '$seller', NULL, 5, 'vault', 'DELETED', 's', NULL, 1, NULL, NULL)",
            "INSERT INTO ${p}items VALUES (5, 'AUCTION', '$seller', NULL, 5, 'vault', 'DELETED', 's', NULL, 2, NULL, NULL)",
            "INSERT INTO ${p}items VALUES (6, 'BID', '$seller', NULL, 5, 'vault', 'LISTED', 's', NULL, 0, NULL, NULL)",
            "INSERT INTO ${p}auction_items VALUES (10, 1, 'A'), (11, 1, 'B'), (12, 2, 'C'), (13, 3, 'D'), (14, 4, 'E'), (15, 5, 'F'), (16, 6, 'G')",
            "INSERT INTO ${p}transactions VALUES (7, 2, '$seller', 'vault', 0, 0, 4.75, 'PENDING', NULL), (8, 2, '$buyer', 'vault', 0, 0, -5, 'RETRIEVED', NULL)",
            "INSERT INTO ${p}logs VALUES (9, 2, 'PURCHASE', '$buyer', '$seller', 'C;C2', 5, 'vault', NULL), (10, 1, 'SALE', '$seller', NULL, 'A', 10, 'vault', NULL)",
        )
        val data = ZahV4Reader({ ds.connection }, p) { 42L }.read()
        val byId = data.records.associateBy { it.id }
        assertEquals(setOf("item:1", "item:2", "item:3", "item:5", "tx:7", "log:9"), byId.keys)
        val listing = byId["item:1"] as SourceListing
        assertEquals(listOf("A", "B"), listing.items.map { it.raw })
        assertEquals("Seller", listing.sellerName)
        assertEquals(0, BigDecimal("10.50").compareTo(listing.price))
        assertTrue(listing.endsAt > System.currentTimeMillis())
        assertEquals(buyer, (byId["item:2"] as SourceClaim).owner)
        assertEquals(ClaimReason.EXPIRED, (byId["item:5"] as SourceClaim).reason, "taken by an unfinished sale: back to the seller")
        assertEquals(0, BigDecimal("4.75").compareTo((byId["tx:7"] as SourceClaim).amount))
        val sale = byId["log:9"] as SourceSale
        assertEquals(seller, sale.seller)
        assertEquals(2, sale.items.size)
    }

    @Test fun `v3 SQL rows`() {
        exec(
            "CREATE TABLE zauctionhouse_items (id VARCHAR(36), itemstack CLOB, price BIGINT, seller VARCHAR(36), buyer VARCHAR(36), " +
                "economy VARCHAR(64), auction_type VARCHAR(16), expire_at BIGINT, storage_type VARCHAR(16), sellerName VARCHAR(16), server_name VARCHAR(64), priority INT)",
            "CREATE TABLE zauctionhouse_transactions (id INT, seller VARCHAR(36), buyer VARCHAR(36), itemstack CLOB, transaction_date BIGINT, " +
                "price BIGINT, economy VARCHAR(64), is_read BOOLEAN, need_money BOOLEAN)",
            "INSERT INTO zauctionhouse_items VALUES ('${UUID.randomUUID()}', 'X;Y', 100, '$seller', 'null', 'VAULT', 'INVENTORY', 0, 'STORAGE', 'Seller', 's', 0)",
            "INSERT INTO zauctionhouse_items VALUES ('${UUID.randomUUID()}', 'Z', 100, '$seller', '$buyer', 'VAULT', 'DEFAULT', 0, 'BUY', 'Seller', 's', 0)",
            "INSERT INTO zauctionhouse_transactions VALUES (1, '$seller', '$buyer', 'Z', 1000, 100, 'VAULT', false, true)",
        )
        val data = ZahV3Reader.sql({ ds.connection }, "zauctionhouse_") { 42L }.read()
        val listing = data.records.filterIsInstance<SourceListing>().single()
        assertEquals(2, listing.items.size)
        assertEquals(42L, listing.endsAt, "no end: the sale duration applies")
        assertEquals(buyer, data.records.filterIsInstance<SourceClaim>().first { it.amount == null }.owner)
        assertEquals(0, BigDecimal(100).compareTo(data.records.filterIsInstance<SourceClaim>().single { it.amount != null }.amount))
        assertEquals(1, data.records.filterIsInstance<SourceSale>().size)
    }

    @Test fun `v3 JSON files`() {
        val dir = Files.createTempDirectory("zahv3").toFile()
        File(dir, "items.json").writeText("""[{"uniqueId":"${UUID.randomUUID()}","itemStack":"A","price":7,"sellerUniqueId":"$seller","expireAt":99999999999999,"sellerName":"Seller"}]""")
        File(dir, "expired_items.json").writeText("""[{"id":"${UUID.randomUUID()}","itemstack":"B","price":7,"seller":"$seller"}]""")
        File(dir, "transactions.json").writeText("""[{"id":3,"seller":"$seller","buyer":"$buyer","itemstack":"A","date":5,"price":7,"need_money":false}]""")
        val data = ZahV3Reader.json(dir) { 42L }.read()
        assertEquals(99999999999999, data.records.filterIsInstance<SourceListing>().single().endsAt)
        assertEquals(ClaimReason.EXPIRED, data.records.filterIsInstance<SourceClaim>().single().reason)
        assertEquals(1, data.records.filterIsInstance<SourceSale>().size)
        dir.deleteRecursively()
    }
}
