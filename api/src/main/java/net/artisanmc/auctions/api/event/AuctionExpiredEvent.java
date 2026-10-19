package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A listing ended unsold; its items are in the seller's claims.
 */
@Beta
public class AuctionExpiredEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionListing listing;

    public AuctionExpiredEvent(AuctionListing listing) { this.listing = listing; }

    public AuctionListing listing() { return listing; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
