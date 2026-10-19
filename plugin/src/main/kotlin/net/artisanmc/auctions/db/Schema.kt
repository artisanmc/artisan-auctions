package net.artisanmc.auctions.db

/**
 * The add-on's tables in the suite's shared database (prefix `auc_`). SQL stays
 * in the subset H2, MySQL and MariaDB share: no window functions, no
 * `CREATE INDEX IF NOT EXISTS` (MySQL lacks it, [Migrations] checks instead).
 * Amounts are DECIMAL(19,2), times are epoch milliseconds, ids are UUID strings.
 *
 * Forward-only: a shipped migration is never edited, a change is a new one.
 */
object Schema {

    class Migration(val version: Int, val description: String, val statements: List<String>)

    val migrations: List<Migration> = listOf(
        Migration(1, "listings, bids, sales, claims, import map", listOf(
            """
            CREATE TABLE IF NOT EXISTS auc_listings (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                server VARCHAR(64) NOT NULL,
                listing_type VARCHAR(16) NOT NULL,
                status VARCHAR(16) NOT NULL,
                seller_uuid VARCHAR(36) NOT NULL,
                seller_name VARCHAR(32) NOT NULL,
                currency VARCHAR(64) NOT NULL,
                price DECIMAL(19,2),
                start_price DECIMAL(19,2),
                current_bid DECIMAL(19,2),
                current_bidder VARCHAR(36),
                bid_count INT NOT NULL DEFAULT 0,
                created_at BIGINT NOT NULL,
                ends_at BIGINT NOT NULL,
                closed_at BIGINT,
                row_version INT NOT NULL DEFAULT 0
            )
            """,
            "CREATE INDEX auc_listings_status_ends ON auc_listings (status, ends_at)",
            "CREATE INDEX auc_listings_status_type ON auc_listings (status, listing_type, created_at)",
            "CREATE INDEX auc_listings_seller ON auc_listings (seller_uuid, status)",
            // One row per stack: a plain listing has one, a lot several. The
            // icon shown for a lot is slot 0.
            """
            CREATE TABLE IF NOT EXISTS auc_listing_items (
                listing_id VARCHAR(36) NOT NULL,
                slot_index INT NOT NULL,
                item_blob TEXT NOT NULL,
                item_display TEXT NOT NULL,
                price_key VARCHAR(255) NOT NULL,
                quantity INT NOT NULL,
                PRIMARY KEY (listing_id, slot_index)
            )
            """,
            // `pending` = intent written before the money moves (reconciled at
            // start-up), `held` = escrowed, then `refunded` or `captured`.
            """
            CREATE TABLE IF NOT EXISTS auc_bids (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                listing_id VARCHAR(36) NOT NULL,
                bidder_uuid VARCHAR(36) NOT NULL,
                currency VARCHAR(64) NOT NULL,
                amount DECIMAL(19,2) NOT NULL,
                state VARCHAR(16) NOT NULL,
                created_at BIGINT NOT NULL
            )
            """,
            "CREATE INDEX auc_bids_listing ON auc_bids (listing_id, state)",
            "CREATE INDEX auc_bids_state ON auc_bids (state, created_at)",
            // Concluded sales: the median reads this table, one row per sold listing.
            """
            CREATE TABLE IF NOT EXISTS auc_sales (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                listing_id VARCHAR(36) NOT NULL,
                server VARCHAR(64) NOT NULL,
                listing_type VARCHAR(16) NOT NULL,
                price_key VARCHAR(255) NOT NULL,
                currency VARCHAR(64) NOT NULL,
                quantity INT NOT NULL,
                total DECIMAL(19,2) NOT NULL,
                unit_price DECIMAL(19,2) NOT NULL,
                seller_uuid VARCHAR(36) NOT NULL,
                buyer_uuid VARCHAR(36),
                sold_at BIGINT NOT NULL
            )
            """,
            "CREATE INDEX auc_sales_key ON auc_sales (price_key, currency, sold_at)",
            """
            CREATE TABLE IF NOT EXISTS auc_claims (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                server VARCHAR(64) NOT NULL,
                owner_uuid VARCHAR(36) NOT NULL,
                claim_kind VARCHAR(16) NOT NULL,
                item_blob TEXT,
                currency VARCHAR(64),
                amount DECIMAL(19,2),
                reason VARCHAR(32) NOT NULL,
                listing_id VARCHAR(36),
                created_at BIGINT NOT NULL,
                claimed_at BIGINT
            )
            """,
            "CREATE INDEX auc_claims_owner ON auc_claims (owner_uuid, claimed_at)",
            // Imports are replayable: what was already imported is skipped.
            """
            CREATE TABLE IF NOT EXISTS auc_import_map (
                source VARCHAR(64) NOT NULL,
                source_id VARCHAR(128) NOT NULL,
                target_id VARCHAR(36) NOT NULL,
                imported_at BIGINT NOT NULL,
                PRIMARY KEY (source, source_id)
            )
            """,
        )),
    )
}
