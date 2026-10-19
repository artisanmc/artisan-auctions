package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A seller or an admin is about to withdraw a listing, bids included.
 */
@Beta
public class AuctionPreCancelEvent extends CancellableAuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer actor;
    private final AuctionListing listing;
    private final boolean admin;

    public AuctionPreCancelEvent(OfflinePlayer actor, AuctionListing listing, boolean admin) {
        this.actor = actor;
        this.listing = listing;
        this.admin = admin;
    }

    public OfflinePlayer actor() { return actor; }

    public AuctionListing listing() { return listing; }

    /** True when an admin withdraws someone else's listing. */
    public boolean isAdmin() { return admin; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
