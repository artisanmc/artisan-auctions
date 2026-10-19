package net.artisanmc.auctions.api;

import java.math.BigDecimal;
import net.artisanmc.modules.api.annotations.Beta;

/**
 * Median unit price of an item in one currency, with what it rests on.
 *
 * @param price      median price of one item
 * @param sales      number of sales counted, after the per-seller cap
 * @param windowDays days of history those sales were taken from
 */
@Beta
public record MedianPrice(BigDecimal price, int sales, int windowDays) {}
