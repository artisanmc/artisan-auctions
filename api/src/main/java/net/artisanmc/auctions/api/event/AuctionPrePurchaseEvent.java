package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A player confirmed buying a listing at its fixed or buy-now price, before
 * any money is taken. For rules that depend on the buyer (spending caps).
 */
@Beta
public class AuctionPrePurchaseEvent extends CancellableAuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player buyer;
    private final AuctionListing listing;
    private final BigDecimal price;

    public AuctionPrePurchaseEvent(Player buyer, AuctionListing listing, BigDecimal price) {
        this.buyer = buyer;
        this.listing = listing;
        this.price = price;
    }

    public Player buyer() { return buyer; }

    public AuctionListing listing() { return listing; }

    public BigDecimal price() { return price; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
