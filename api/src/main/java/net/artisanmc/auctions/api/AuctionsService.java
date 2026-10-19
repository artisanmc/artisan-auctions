package net.artisanmc.auctions.api;

import java.util.List;
import net.artisanmc.modules.api.annotations.Beta;

/**
 * Entry point for other plugins, registered in Bukkit's {@code ServicesManager}
 * when Artisan Auctions is enabled:
 *
 * <pre>{@code
 * AuctionsService auctions = Bukkit.getServicesManager().load(AuctionsService.class);
 * }</pre>
 *
 * Reads only. Listings, prices and sales history are added with the features
 * that produce them; changes go through the cancellable events of this package.
 */
@Beta
public interface AuctionsService {

    /** Whether players can create listings of {@code type} right now. */
    boolean isEnabled(ListingType type);

    /** Currencies a listing may use, in the configured order. Never empty. */
    List<String> currencies();

    /** Currency of a listing whose seller picked none. */
    String defaultCurrency();
}
