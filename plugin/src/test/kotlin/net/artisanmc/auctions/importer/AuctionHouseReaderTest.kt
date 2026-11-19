package net.artisanmc.auctions.importer

import net.artisanmc.auctions.market.ClaimReason
import org.h2.jdbcx.JdbcDataSource
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*

class AuctionHouseReaderTest {
    private val ds = JdbcDataSource().apply { setURL("jdbc:h2:mem:ah_${System.nanoTime()};DB_CLOSE_DELAY=-1") }
    private val seller = UUID.randomUUID()
    private val bidder = UUID.randomUUID()
    private val p = "auctionhouse_"

    private fun exec(vararg sql: String) = ds.connection.use { c -> c.createStatement().use { s -> sql.forEach(s::execute) } }

    private fun row(id: String, owner: UUID, bidder: UUID, base: String, expired: Boolean, version: Int = 0,
                    request: Boolean = false, server: Boolean = false, currency: String = "Vault/Vault", currencyItem: String? = null) =
        "INSERT INTO ${p}auctions VALUES ('$id', '$owner', 'Seller', '$bidder', 'Seller', 'MISC', $base, -1, 1, 0, $expired, 9000000, " +
            "'STONE', 'Stone', NULL, NULL, 'i: yaml-$id', ${if (version == 1) "'{id:\"minecraft:stone\",count:1}'" else "NULL"}, $version, " +
            "$request, $server, FALSE, '$currency', ${currencyItem?.let { "'$it'" } ?: "NULL"}, 1000)"

    @Test fun `listings, collection bins, payments and sales are read, the rest left aside`() {
        exec(
            "CREATE TABLE ${p}auctions (id VARCHAR(36), owner VARCHAR(36), owner_name VARCHAR(16), highest_bidder VARCHAR(36), " +
                "highest_bidder_name VARCHAR(16), category VARCHAR(48), base_price DOUBLE, bid_start_price DOUBLE, bid_increment_price DOUBLE, " +
                "current_price DOUBLE, expired BOOLEAN, expires_at BIGINT, item_material VARCHAR(48), item_name TEXT, item_lore TEXT, " +
                "item_enchants TEXT, item TEXT, itemstack TEXT, serialize_version INT, is_request BOOLEAN, server_auction BOOLEAN, " +
                "infinite BOOLEAN, currency VARCHAR(70), currency_item TEXT, created_at BIGINT)",
            "CREATE TABLE ${p}payments (uuid VARCHAR(36), payment_for VARCHAR(36), amount DOUBLE, time BIGINT, item TEXT, " +
                "from_name VARCHAR(48), reason VARCHAR(48), itemstack TEXT, serialize_version INT, currency VARCHAR(70), currency_item TEXT)",
            "CREATE TABLE ${p}transactions (id VARCHAR(36), seller VARCHAR(36), seller_name VARCHAR(16), buyer VARCHAR(36), " +
                "buyer_name VARCHAR(16), transaction_time BIGINT, item TEXT, auction_sale_type VARCHAR(32), final_price DOUBLE, " +
                "itemstack TEXT, serialize_version INT)",
            row("a1", seller, seller, "12.5", expired = false),
            row("a2", seller, seller, "5", expired = true, version = 1),
            row("a3", seller, bidder, "5", expired = false),
            row("a4", seller, seller, "-1", expired = false),
            row("a5", seller, seller, "5", expired = false, request = true),
            row("a6", seller, seller, "5", expired = false, server = true),
            row("a7", seller, seller, "5", expired = false, currency = "CoinsEngine/gems"),
            row("a8", seller, seller, "5", expired = false, currencyItem = "DIAMOND"),
            "INSERT INTO ${p}payments VALUES ('p1', '$seller', 40, 2000, NULL, 'Buyer', 'ITEM_SOLD', NULL, 0, 'Vault/Vault', NULL)",
            "INSERT INTO ${p}payments VALUES ('p2', '$seller', 0, 2000, NULL, 'Buyer', 'ITEM_SOLD', NULL, 0, 'Vault/Vault', NULL)",
            "INSERT INTO ${p}transactions VALUES ('t1', '$seller', 'Seller', '$bidder', 'Bidder', 3000, 'i: yaml-t1', 'USED_BIN', 30, NULL, 0)",
        )
        val data = AuctionHouseReader({ ds.connection }, p).read()
        val byId = data.records.associateBy { it.id }

        val l = assertIs<SourceListing>(byId["auction:a1"])
        assertEquals(0, BigDecimal("12.5").compareTo(l.price))
        assertEquals("vault", l.currency)
        assertEquals(9000000, l.endsAt)
        assertEquals(SourceItem("i: yaml-a1", "bukkit_yaml"), l.items.single())

        val bin = assertIs<SourceClaim>(byId["auction:a2"])
        assertEquals(ClaimReason.IMPORTED, bin.reason)
        assertEquals("snbt", bin.items.single().encoding)

        assertEquals(ClaimReason.EXPIRED, assertIs<SourceClaim>(byId["auction:a4"]).reason, "no buy-now price: back to the seller")
        assertEquals("gems", assertIs<SourceListing>(byId["auction:a7"]).currency)
        assertEquals(0, BigDecimal(40).compareTo(assertIs<SourceClaim>(byId["payment:p1"]).amount))
        assertNull(byId["payment:p2"])
        assertEquals(bidder, assertIs<SourceSale>(byId["transaction:t1"]).buyer)

        assertEquals(
            mapOf("auction:a3" to "active_bid", "auction:a5" to "buy_request", "auction:a6" to "server_listing", "auction:a8" to "item_currency"),
            data.rejected.associate { it.id to it.code },
        )
    }

    @Test fun `an older AuctionHouse without the later columns still reads`() {
        exec(
            "CREATE TABLE ${p}auctions (id VARCHAR(36), owner VARCHAR(36), owner_name VARCHAR(16), highest_bidder VARCHAR(36), " +
                "highest_bidder_name VARCHAR(16), base_price DOUBLE, expired BOOLEAN, expires_at BIGINT, item TEXT)",
            "INSERT INTO ${p}auctions VALUES ('a1', '$seller', 'S', '$seller', 'S', 3, FALSE, 5000, 'i: x')",
        )
        val data = AuctionHouseReader({ ds.connection }, p).read()
        val l = assertIs<SourceListing>(data.records.single())
        assertEquals("vault", l.currency)
        assertEquals(0, l.createdAt)
    }
}
