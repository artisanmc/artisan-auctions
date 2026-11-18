package net.artisanmc.auctions.api.event;

import net.artisanmc.modules.api.annotations.Beta;
import org.bukkit.event.Event;

/**
 * Base of the add-on's events. Fired on the main thread; on Folia, {@link AuctionPreListEvent} on the
 * seller's region thread and every other event on the global region thread.
 */
@Beta
public abstract class AuctionEvent extends Event {
    protected AuctionEvent() {
        super(false);
    }
}
