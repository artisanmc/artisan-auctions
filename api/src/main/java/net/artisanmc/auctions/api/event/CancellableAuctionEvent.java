package net.artisanmc.auctions.api.event;

import net.artisanmc.modules.api.annotations.Beta;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.Nullable;

/**
 * Fired before any money or item moves: cancelling leaves nothing to undo.
 * {@link #denyText(String)} (any server) or {@link #deny(Component)} (Paper) cancels
 * with the message the player sees; without one, the add-on shows its generic refusal.
 */
@Beta
public abstract class CancellableAuctionEvent extends AuctionEvent implements Cancellable {
    private boolean cancelled;
    // A Component or a String: typed Object so the class loads on Spigot, which has no Adventure.
    private @Nullable Object denyMessage;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /** Cancels and sets the message shown to the player. Paper only: Spigot has no Adventure. */
    public void deny(@Nullable Component message) {
        this.cancelled = true;
        this.denyMessage = message;
    }

    /**
     * Cancels and sets the message shown to the player, in MiniMessage ({@code §} codes accepted).
     */
    public void denyText(@Nullable String miniMessage) {
        this.cancelled = true;
        this.denyMessage = miniMessage;
    }

    /** The message given to {@link #deny(Component)}, or null. Paper only. */
    public @Nullable Component denyMessage() {
        return denyMessage instanceof Component c ? c : null;
    }

    /** The message given to {@link #denyText(String)}, or null. */
    public @Nullable String denyText() {
        return denyMessage instanceof String s ? s : null;
    }

    /** What the add-on shows: the String or the Component, whichever was given. */
    public @Nullable Object denial() {
        return denyMessage;
    }
}
