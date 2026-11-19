package net.artisanmc.auctions.importer

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream

/**
 * An item's NBT written as text (SNBT, `{id:"minecraft:stone",count:3,components:{…}}`), as
 * item-nbt-api and `/data` print it, turned into the gzipped binary NBT that Paper's
 * `ItemStack.deserializeBytes` reads. Paper upgrades it from its `DataVersion` (added from
 * [dataVersion] when the text has none). Pure, no Bukkit: tested on its own.
 */
object Snbt {
    sealed interface Tag
    data class TByte(val v: Byte) : Tag
    data class TShort(val v: Short) : Tag
    data class TInt(val v: Int) : Tag
    data class TLong(val v: Long) : Tag
    data class TFloat(val v: Float) : Tag
    data class TDouble(val v: Double) : Tag
    data class TString(val v: String) : Tag
    data class TList(val items: List<Tag>) : Tag
    data class TCompound(val entries: LinkedHashMap<String, Tag>) : Tag
    class TByteArray(val v: ByteArray) : Tag
    class TIntArray(val v: IntArray) : Tag
    class TLongArray(val v: LongArray) : Tag

    class SnbtException(message: String) : IllegalArgumentException(message)

    /** Gzipped binary NBT of the compound [text], with a `DataVersion` if it has none. */
    fun toPaperBytes(text: String, dataVersion: Int): ByteArray {
        val root = parse(text) as? TCompound ?: throw SnbtException("not a compound")
        if ("DataVersion" !in root.entries) root.entries["DataVersion"] = TInt(dataVersion)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeByte(10); out.writeUTF(""); writePayload(out, root)
        }
        return bytes.toByteArray()
    }

    fun parse(text: String): Tag {
        val p = Parser(text)
        val tag = p.value()
        p.skipWs()
        if (!p.done()) throw SnbtException("trailing text at ${p.i}")
        return tag
    }

    private class Parser(val s: String) {
        var i = 0
        fun done() = i >= s.length
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun peek(): Char { skipWs(); if (done()) throw SnbtException("unexpected end"); return s[i] }
        fun expect(c: Char) { if (peek() != c) throw SnbtException("expected '$c' at $i"); i++ }

        fun value(): Tag = when (peek()) {
            '{' -> compound()
            '[' -> list()
            '"', '\'' -> TString(quoted())
            else -> scalar(unquoted())
        }

        fun compound(): TCompound {
            expect('{')
            val map = LinkedHashMap<String, Tag>()
            if (peek() == '}') { i++; return TCompound(map) }
            while (true) {
                val key = if (peek() == '"' || peek() == '\'') quoted() else unquoted()
                if (key.isEmpty()) throw SnbtException("empty key at $i")
                expect(':')
                map[key] = value()
                when (peek()) { ',' -> i++; '}' -> { i++; return TCompound(map) }; else -> throw SnbtException("expected ',' or '}' at $i") }
            }
        }

        fun list(): Tag {
            expect('[')
            skipWs()
            if (i + 1 < s.length && s[i + 1] == ';' && s[i] in "BIL") {
                val type = s[i]; i += 2
                val nums = mutableListOf<Long>()
                if (peek() != ']') while (true) {
                    val raw = unquoted().trimEnd('b', 'B', 'l', 'L', 's', 'S')
                    nums += raw.toLongOrNull() ?: throw SnbtException("bad number '$raw' in array at $i")
                    when (peek()) { ',' -> i++; ']' -> break; else -> throw SnbtException("expected ',' or ']' at $i") }
                }
                i++
                return when (type) {
                    'B' -> TByteArray(ByteArray(nums.size) { nums[it].toByte() })
                    'I' -> TIntArray(IntArray(nums.size) { nums[it].toInt() })
                    else -> TLongArray(nums.toLongArray())
                }
            }
            val items = mutableListOf<Tag>()
            if (peek() == ']') { i++; return TList(items) }
            while (true) {
                items += value()
                when (peek()) { ',' -> i++; ']' -> { i++; return TList(items) }; else -> throw SnbtException("expected ',' or ']' at $i") }
            }
        }

        fun quoted(): String {
            val q = s[i++]
            val b = StringBuilder()
            while (true) {
                if (done()) throw SnbtException("unterminated string")
                val c = s[i++]
                when {
                    c == q -> return b.toString()
                    c == '\\' -> { if (done()) throw SnbtException("unterminated string"); b.append(s[i++]) }
                    else -> b.append(c)
                }
            }
        }

        fun unquoted(): String {
            skipWs()
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "_-.+")) i++
            return s.substring(start, i)
        }

        fun scalar(raw: String): Tag {
            if (raw.isEmpty()) throw SnbtException("value expected at $i")
            when (raw.lowercase()) { "true" -> return TByte(1); "false" -> return TByte(0) }
            val body = raw.dropLast(1)
            return when (raw.last()) {
                'b', 'B' -> body.toByteOrNull()?.let(::TByte)
                's', 'S' -> body.toShortOrNull()?.let(::TShort)
                'l', 'L' -> body.toLongOrNull()?.let(::TLong)
                'f', 'F' -> body.toFloatOrNull()?.let(::TFloat)
                'd', 'D' -> body.toDoubleOrNull()?.let(::TDouble)
                else -> raw.toIntOrNull()?.let(::TInt) ?: if (NUMBER.matches(raw)) raw.toDoubleOrNull()?.let(::TDouble) else null
            } ?: TString(raw)
        }
    }

    private val NUMBER = Regex("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?")

    private fun id(t: Tag): Int = when (t) {
        is TByte -> 1; is TShort -> 2; is TInt -> 3; is TLong -> 4; is TFloat -> 5; is TDouble -> 6
        is TByteArray -> 7; is TString -> 8; is TList -> 9; is TCompound -> 10; is TIntArray -> 11; is TLongArray -> 12
    }

    private fun writePayload(out: DataOutputStream, t: Tag) {
        when (t) {
            is TByte -> out.writeByte(t.v.toInt())
            is TShort -> out.writeShort(t.v.toInt())
            is TInt -> out.writeInt(t.v)
            is TLong -> out.writeLong(t.v)
            is TFloat -> out.writeFloat(t.v)
            is TDouble -> out.writeDouble(t.v)
            is TString -> out.writeUTF(t.v)
            is TByteArray -> { out.writeInt(t.v.size); out.write(t.v) }
            is TIntArray -> { out.writeInt(t.v.size); t.v.forEach(out::writeInt) }
            is TLongArray -> { out.writeInt(t.v.size); t.v.forEach(out::writeLong) }
            is TList -> {
                val type = t.items.firstOrNull()?.let(::id) ?: 0
                if (t.items.any { id(it) != type }) throw SnbtException("a list mixes tag types")
                out.writeByte(type); out.writeInt(t.items.size)
                t.items.forEach { writePayload(out, it) }
            }
            is TCompound -> {
                for ((k, v) in t.entries) { out.writeByte(id(v)); out.writeUTF(k); writePayload(out, v) }
                out.writeByte(0)
            }
        }
    }
}
