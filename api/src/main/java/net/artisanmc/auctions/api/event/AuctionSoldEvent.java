package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A sale was bought or an auction was won. The money and items are in the
 * buyer's and seller's claims, or already delivered.
 */
@Beta
public class AuctionSoldEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionListing listing;
    private final UUID buyer;
    private final BigDecimal price;
    private final BigDecimal tax;

    public AuctionSoldEvent(AuctionListing listing, UUID buyer, BigDecimal price, BigDecimal tax) {
        this.listing = listing;
        this.buyer = buyer;
        this.price = price;
        this.tax = tax;
    }

    public AuctionListing listing() { return listing; }

    public UUID buyer() { return buyer; }

    /** What the buyer paid. */
    public BigDecimal price() { return price; }

    /** Kept by the house; the seller received {@code price - tax}. */
    public BigDecimal tax() { return tax; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
