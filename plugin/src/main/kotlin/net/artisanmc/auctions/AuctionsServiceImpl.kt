package net.artisanmc.auctions

import net.artisanmc.auctions.api.AuctionsService
import net.artisanmc.auctions.api.ListingType
import net.artisanmc.auctions.config.ConfigStore

class AuctionsServiceImpl(private val config: ConfigStore) : AuctionsService {
    override fun isEnabled(type: ListingType): Boolean = when (type) {
        ListingType.SALE -> config.current.sales.enabled
        ListingType.AUCTION -> config.current.auctions.enabled
    }

    override fun currencies(): List<String> = config.current.currencies.allowed

    override fun defaultCurrency(): String = config.current.currencies.default
}
