package net.artisanmc.auctions.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.inventory.ItemStack;

/** A listing as other plugins see it: an immutable snapshot. */
@Beta
public interface AuctionListing {
    UUID id();

    ListingType type();

    UUID seller();

    String sellerName();

    /** Currency id of the core registry ({@code vault}, {@code playerpoints}…). */
    String currency();

    /** Fixed price of a sale, or the buy-now price of an auction when it has one. */
    Optional<BigDecimal> price();

    /** Opening bid of an auction; empty for a sale. */
    Optional<BigDecimal> startPrice();

    /** Leading bid of an auction; empty without bids. */
    Optional<BigDecimal> currentBid();

    Optional<UUID> currentBidder();

    int bidCount();

    /** Copies of the listed stacks: one for a plain listing, several for a lot. */
    List<ItemStack> items();

    Instant createdAt();

    Instant endsAt();
}
