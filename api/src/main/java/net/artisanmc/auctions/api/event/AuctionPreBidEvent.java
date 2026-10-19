package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A player is about to bid, before the amount is held. A price limit on
 * auctions belongs here: the final price does not exist when they are listed.
 */
@Beta
public class AuctionPreBidEvent extends CancellableAuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player bidder;
    private final AuctionListing listing;
    private final BigDecimal amount;

    public AuctionPreBidEvent(Player bidder, AuctionListing listing, BigDecimal amount) {
        this.bidder = bidder;
        this.listing = listing;
        this.amount = amount;
    }

    public Player bidder() { return bidder; }

    public AuctionListing listing() { return listing; }

    public BigDecimal amount() { return amount; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
