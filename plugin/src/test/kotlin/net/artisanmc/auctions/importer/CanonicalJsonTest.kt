package net.artisanmc.auctions.importer

import net.artisanmc.auctions.market.ClaimReason
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*

class CanonicalJsonTest {
    private val seller = UUID.randomUUID()
    private val buyer = UUID.randomUUID()
    private val item = """[{"encoding": "bukkit_yaml", "data": "i: x"}]"""

    @Test fun `the canonical file reads into records, a wrong one is reported with its path`() {
        val doc = CanonicalJson.read("""
            {"format": "auctions/1", "source": "myplugin",
             "listings": [
               {"id": "1", "seller": "$seller", "seller_name": "Steve", "currency": "vault", "price": "12.50", "created_at": 1, "ends_at": 2, "items": $item},
               {"id": "2", "seller": "nope", "currency": "vault", "price": "1", "created_at": 1, "ends_at": 2, "items": $item},
               {"id": "3", "seller": "$seller", "currency": "vault", "price": "1.005", "created_at": 1, "ends_at": 2, "items": $item}],
             "claims": [
               {"id": "4", "owner": "$buyer", "reason": "expired", "created_at": 3, "items": $item},
               {"id": "5", "owner": "$seller", "reason": "sold", "created_at": 3, "currency": "vault", "amount": "40"},
               {"id": "6", "owner": "$seller", "reason": "lost", "created_at": 3, "items": $item}],
             "sales": [
               {"id": "7", "seller": "$seller", "buyer": "$buyer", "currency": "vault", "total": 30, "sold_at": 4,
                "items": [{"encoding": "nbt", "data": "x"}]},
               {"id": "8", "seller": "$seller", "currency": "vault", "total": 30, "sold_at": 4, "items": [{"encoding": "snbt", "data": "{}"}]}]}
        """.trimIndent().reader())
        assertEquals("myplugin", doc.source)
        val ids = doc.data.records.map { it.id }
        assertEquals(listOf("listing:1", "claim:4", "claim:5", "sale:8"), ids)
        val l = doc.data.records[0] as SourceListing
        assertEquals(0, BigDecimal("12.5").compareTo(l.price))
        assertEquals(SourceItem("i: x", "bukkit_yaml"), l.items.single())
        assertEquals(ClaimReason.SOLD, (doc.data.records[2] as SourceClaim).reason)
        assertNull((doc.data.records[3] as SourceSale).buyer)
        assertEquals(4, doc.data.problems.size)
        assertTrue(doc.data.problems[0].startsWith("listings[1]: listings[1].seller: not a UUID"), doc.data.problems[0])
        assertTrue(doc.data.problems.any { "more than two decimals" in it })
        assertTrue(doc.data.problems.any { "unknown 'lost'" in it })
        assertTrue(doc.data.problems.any { "'nbt' is not one of" in it })
    }

    @Test fun `an unknown format or source is refused whole`() {
        assertFailsWith<CanonicalJson.FormatException> { CanonicalJson.read("""{"format": "auctions/2", "source": "x"}""".reader()) }
        assertFailsWith<CanonicalJson.FormatException> { CanonicalJson.read("""{"format": "auctions/1", "source": "Bad Name"}""".reader()) }
        assertFailsWith<CanonicalJson.FormatException> { CanonicalJson.read("[]".reader()) }
        assertFailsWith<CanonicalJson.FormatException> { CanonicalJson.read("{".reader()) }
    }
}
