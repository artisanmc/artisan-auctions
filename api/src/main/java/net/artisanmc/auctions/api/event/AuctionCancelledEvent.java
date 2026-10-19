package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A listing was withdrawn; items went back to the seller, bids to their bidders.
 */
@Beta
public class AuctionCancelledEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionListing listing;
    private final UUID actor;

    public AuctionCancelledEvent(AuctionListing listing, UUID actor) {
        this.listing = listing;
        this.actor = actor;
    }

    public AuctionListing listing() { return listing; }

    public UUID actor() { return actor; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
