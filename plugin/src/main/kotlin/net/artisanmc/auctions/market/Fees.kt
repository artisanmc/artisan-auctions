package net.artisanmc.auctions.market

import net.artisanmc.auctions.config.AuctionsConfig
import java.math.BigDecimal
import java.math.RoundingMode

object Fees {
    private val HUNDRED = BigDecimal(100)

    fun money(v: BigDecimal): BigDecimal = v.setScale(2, RoundingMode.HALF_UP)

    /** percent of [base] + flat, never more than [base] when [capped]. */
    fun of(base: BigDecimal, fee: AuctionsConfig.Fee, capped: Boolean = true): BigDecimal {
        val raw = money(base * fee.percent / HUNDRED + fee.flat)
        return if (capped && raw > base) money(base) else raw
    }

    /** What the seller receives for [price]. */
    fun net(price: BigDecimal, tax: AuctionsConfig.Fee): BigDecimal = money(price - of(price, tax))

    /** Lowest acceptable next bid. */
    fun minBid(start: BigDecimal, current: BigDecimal?, inc: AuctionsConfig.Increment): BigDecimal {
        if (current == null) return money(start)
        val step = (current * inc.percent / HUNDRED).max(inc.min)
        return money(current + step).max(money(start))
    }

    /** `12.50` → 12.5 typed by a player, at most 2 decimals, positive. */
    fun parseAmount(text: String): BigDecimal? {
        val v = text.trim().replace(",", ".").replace("_", "").toBigDecimalOrNull() ?: return null
        if (v.signum() <= 0 || v.stripTrailingZeros().scale() > 2) return null
        return money(v)
    }
}
