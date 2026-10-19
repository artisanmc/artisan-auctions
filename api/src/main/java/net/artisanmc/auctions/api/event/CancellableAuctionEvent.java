package net.artisanmc.auctions.api.event;

import net.artisanmc.modules.api.annotations.Beta;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.Nullable;

/**
 * Fired before any money or item moves: cancelling leaves nothing to undo.
 * {@link #deny(Component)} cancels with the message the player sees; without
 * one, the add-on shows its generic refusal.
 */
@Beta
public abstract class CancellableAuctionEvent extends AuctionEvent implements Cancellable {
    private boolean cancelled;
    private @Nullable Component denyMessage;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /** Cancels and sets the message shown to the player. */
    public void deny(@Nullable Component message) {
        this.cancelled = true;
        this.denyMessage = message;
    }

    public @Nullable Component denyMessage() {
        return denyMessage;
    }
}
