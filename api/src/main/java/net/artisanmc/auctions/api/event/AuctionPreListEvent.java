package net.artisanmc.auctions.api.event;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.artisanmc.auctions.api.ListingType;
import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A player is about to list items, after the add-on's own checks and before the
 * items or the listing fee are taken. The right place for a price limit: the
 * seller can still change the price. Imported listings do not fire it.
 */
@Beta
public class AuctionPreListEvent extends CancellableAuctionEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player seller;
    private final List<ItemStack> items;
    private final ListingType type;
    private final String currency;
    private final @Nullable BigDecimal median;
    private @Nullable BigDecimal price;
    private @Nullable BigDecimal startPrice;
    private Duration duration;

    public AuctionPreListEvent(Player seller, List<ItemStack> items, ListingType type, String currency,
                               @Nullable BigDecimal price, @Nullable BigDecimal startPrice, Duration duration,
                               @Nullable BigDecimal median) {
        this.seller = seller;
        this.items = List.copyOf(items);
        this.type = type;
        this.currency = currency;
        this.price = price;
        this.startPrice = startPrice;
        this.duration = duration;
        this.median = median;
    }

    public Player seller() { return seller; }

    /** Copies of the stacks being listed. */
    public List<ItemStack> items() { return items; }

    public ListingType type() { return type; }

    public String currency() { return currency; }

    /** Sale price, or the buy-now price of an auction. */
    public Optional<BigDecimal> price() { return Optional.ofNullable(price); }

    public void setPrice(@Nullable BigDecimal price) { this.price = price; }

    /** Opening bid of an auction. */
    public Optional<BigDecimal> startPrice() { return Optional.ofNullable(startPrice); }

    public void setStartPrice(@Nullable BigDecimal startPrice) { this.startPrice = startPrice; }

    public Duration duration() { return duration; }

    public void setDuration(Duration duration) { this.duration = Objects.requireNonNull(duration); }

    /** Median unit price of this item in this currency, when enough sales exist. */
    public Optional<BigDecimal> median() { return Optional.ofNullable(median); }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
