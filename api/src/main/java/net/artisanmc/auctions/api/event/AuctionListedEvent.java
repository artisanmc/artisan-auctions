package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A listing was saved and is visible.
 */
@Beta
public class AuctionListedEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionListing listing;

    public AuctionListedEvent(AuctionListing listing) { this.listing = listing; }

    public AuctionListing listing() { return listing; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
