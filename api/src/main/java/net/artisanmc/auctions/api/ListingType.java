package net.artisanmc.auctions.api;

import net.artisanmc.modules.api.annotations.Beta;

/** What a listing is: a fixed-price sale or a time-limited auction. */
@Beta
public enum ListingType {
    SALE,
    AUCTION;

    /** The id used in commands, data sources and the database (`sale`, `auction`). */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
