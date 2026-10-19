package net.artisanmc.auctions.api;

import java.util.List;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import net.artisanmc.modules.api.annotations.Beta;

/**
 * Entry point for other plugins, registered in Bukkit's {@code ServicesManager}
 * when Artisan Auctions is enabled:
 *
 * <pre>{@code
 * AuctionsService auctions = Bukkit.getServicesManager().load(AuctionsService.class);
 * }</pre>
 *
 * Reads only: changes go through the cancellable events of this package.
 * Call from the main thread.
 */
@Beta
public interface AuctionsService {

    /** Whether players can create listings of {@code type} right now. */
    boolean isEnabled(ListingType type);

    /** Currencies a listing may use, in the configured order. Never empty. */
    List<String> currencies();

    /** Currency of a listing whose seller picked none. */
    String defaultCurrency();

    /** Active listings of this server, as snapshots. */
    List<AuctionListing> activeListings();

    /**
     * The identity the median gives {@code stack}: an item plugin id, the name and
     * lore of a custom item, or the material and enchantments of a vanilla one.
     */
    String priceKey(ItemStack stack);

    /**
     * Median unit price of {@code stack} in {@code currency}, from the cache. Empty
     * when the median is disabled, when there are too few sales, or while it is
     * first computed (ask again a moment later).
     */
    Optional<MedianPrice> medianPrice(ItemStack stack, String currency);
}
