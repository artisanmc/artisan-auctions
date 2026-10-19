package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A bidder lost the lead; their bid is being refunded.
 */
@Beta
public class AuctionOutbidEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionListing listing;
    private final UUID previousBidder;
    private final BigDecimal refunded;

    public AuctionOutbidEvent(AuctionListing listing, UUID previousBidder, BigDecimal refunded) {
        this.listing = listing;
        this.previousBidder = previousBidder;
        this.refunded = refunded;
    }

    public AuctionListing listing() { return listing; }

    public UUID previousBidder() { return previousBidder; }

    public BigDecimal refunded() { return refunded; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
