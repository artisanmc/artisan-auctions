package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A bid was accepted and its amount is held.
 */
@Beta
public class AuctionBidPlacedEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionListing listing;
    private final UUID bidder;
    private final BigDecimal amount;

    public AuctionBidPlacedEvent(AuctionListing listing, UUID bidder, BigDecimal amount) {
        this.listing = listing;
        this.bidder = bidder;
        this.amount = amount;
    }

    /** The listing after the bid. */
    public AuctionListing listing() { return listing; }

    public UUID bidder() { return bidder; }

    public BigDecimal amount() { return amount; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
