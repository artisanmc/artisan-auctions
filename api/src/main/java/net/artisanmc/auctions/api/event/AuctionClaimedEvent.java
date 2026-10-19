package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.util.UUID;
import net.artisanmc.auctions.api.AuctionListing;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * A player received an item or an amount from their claims box.
 */
@Beta
public class AuctionClaimedEvent extends AuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID owner;
    private final boolean money;
    private final String currency;
    private final BigDecimal amount;

    public AuctionClaimedEvent(UUID owner, boolean money, String currency, BigDecimal amount) {
        this.owner = owner;
        this.money = money;
        this.currency = currency;
        this.amount = amount;
    }

    public UUID owner() { return owner; }

    /** True for money, false for an item. */
    public boolean isMoney() { return money; }

    /** Currency of a money claim, null for an item. */
    public String currency() { return currency; }

    /** Amount of a money claim, null for an item. */
    public BigDecimal amount() { return amount; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
