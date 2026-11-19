package net.artisanmc.auctions.importer

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.*

class SnbtTest {
    @Test fun `an item's SNBT parses with its typed values`() {
        val t = Snbt.parse("""{id:"minecraft:diamond_sword",count:1,components:{"minecraft:damage":5,"minecraft:custom_name":'{"text":"Ex\'calibur"}',"minecraft:enchantments":{levels:{"minecraft:sharpness":5b}},flags:[I;1,2,3],ok:true,f:1.5f,d:2.0,big:9L,list:[a,b]}}""")
        val root = assertIs<Snbt.TCompound>(t)
        assertEquals(Snbt.TString("minecraft:diamond_sword"), root.entries["id"])
        assertEquals(Snbt.TInt(1), root.entries["count"])
        val c = assertIs<Snbt.TCompound>(root.entries["components"]).entries
        assertEquals(Snbt.TInt(5), c["minecraft:damage"])
        assertEquals(Snbt.TString("""{"text":"Ex'calibur"}"""), c["minecraft:custom_name"])
        assertEquals(Snbt.TByte(5), assertIs<Snbt.TCompound>(assertIs<Snbt.TCompound>(c["minecraft:enchantments"]).entries["levels"]).entries["minecraft:sharpness"])
        assertContentEquals(intArrayOf(1, 2, 3), assertIs<Snbt.TIntArray>(c["flags"]).v)
        assertEquals(Snbt.TByte(1), c["ok"])
        assertEquals(Snbt.TFloat(1.5f), c["f"])
        assertEquals(Snbt.TDouble(2.0), c["d"])
        assertEquals(Snbt.TLong(9), c["big"])
        assertEquals(Snbt.TList(listOf(Snbt.TString("a"), Snbt.TString("b"))), c["list"])
    }

    @Test fun `the binary form is gzipped NBT with a DataVersion`() {
        val bytes = Snbt.toPaperBytes("""{id:"minecraft:stone",count:3}""", 4189)
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { d ->
            assertEquals(10, d.readByte().toInt()); assertEquals("", d.readUTF())
            assertEquals(8, d.readByte().toInt()); assertEquals("id", d.readUTF()); assertEquals("minecraft:stone", d.readUTF())
            assertEquals(3, d.readByte().toInt()); assertEquals("count", d.readUTF()); assertEquals(3, d.readInt())
            assertEquals(3, d.readByte().toInt()); assertEquals("DataVersion", d.readUTF()); assertEquals(4189, d.readInt())
            assertEquals(0, d.readByte().toInt())
        }
        val kept = Snbt.toPaperBytes("""{id:"minecraft:stone",count:1,DataVersion:3953}""", 4189)
        assertTrue(kept.isNotEmpty())
    }

    @Test fun `broken text is refused`() {
        for (bad in listOf("{id:", "{id:\"a\"", "[1,2", "{a:1}}", "{a:[1,b]}", "\"x\"")) {
            assertFailsWith<IllegalArgumentException>(bad) { Snbt.toPaperBytes(bad, 1) }
        }
    }
}
