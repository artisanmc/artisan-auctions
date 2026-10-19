package net.artisanmc.auctions.api;

import java.math.BigDecimal;
import org.bukkit.OfflinePlayer;
import net.artisanmc.modules.api.annotations.Beta;

/**
 * A currency listings can be priced in, under its {@link #id()} (what
 * {@code currencies.allowed} names). Vault's economy is built in as {@code vault}; any other
 * plugin adds one through Bukkit's {@code ServicesManager}:
 *
 * <pre>{@code
 * Bukkit.getServicesManager().register(AuctionCurrency.class, new GemsCurrency(), plugin, ServicePriority.Normal);
 * }</pre>
 *
 * Called on the main thread. {@code amount} is positive; a refused withdrawal or deposit
 * returns false and leaves the balance as it was.
 */
@Beta
public interface AuctionCurrency {

    /** Lowercase id, e.g. {@code gems}. Two registrations of one id: the higher priority wins. */
    String id();

    BigDecimal balance(OfflinePlayer player);

    boolean withdraw(OfflinePlayer player, BigDecimal amount);

    boolean deposit(OfflinePlayer player, BigDecimal amount);
}
