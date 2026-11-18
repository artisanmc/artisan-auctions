package net.artisanmc.auctions.paper

import net.artisanmc.auctions.importer.Importer
import net.artisanmc.modules.api.ArtisanAPI
import net.artisanmc.modules.api.importing.ImportReport
import net.artisanmc.modules.api.importing.ImportRequest
import net.artisanmc.modules.api.importing.ImporterDeclaration
import net.artisanmc.modules.api.importing.Importer as ArtisanImporter

/**
 * The sources of [Imports] registered with Artisan (API 1.7.0), so `/artisan import auctions:<source>`
 * runs them, logs each run and rolls a batch back. Only loaded when Artisan has that API: on an
 * older one, `/ah admin import` runs them itself.
 */
object ArtisanImports {
    const val MIN_API = "1.7.0"

    private val LABELS = mapOf(
        "zauctionhouse" to "zAuctionHouse v4",
        "zauctionhouse-v3" to "zAuctionHouse v3",
        "auctionhouse" to "AuctionHouse (Kiranhart)",
        "json" to "Auctions file (auctions/1)",
    )

    fun register(api: ArtisanAPI, imports: Imports) {
        for ((source, label) in LABELS) {
            api.imports.register(ImporterDeclaration("auctions:$source", label, Imports.CONFLICTS[source].orEmpty(), source == "json", adapter(source, imports)))
        }
    }

    private fun adapter(source: String, imports: Imports) = object : ArtisanImporter {
        override fun plan(request: ImportRequest) = report(imports.execute(source, request.file?.toFile(), true, null), request)
        override fun apply(request: ImportRequest) = report(imports.execute(source, request.file?.toFile(), false, request.batch), request)
        override fun rollback(batch: String, dryRun: Boolean): ImportReport {
            val r = imports.rollback(batch, dryRun)
            val b = ImportReport.builder(dryRun, batch).count("records", "records undone", r.undone.size)
            r.changed.forEach { b.reject(it, "changed_since", "bought, bid on or claimed since the import: kept") }
            if (r.gone.isNotEmpty()) b.note("${r.gone.size} record(s) were already undone")
            return b.build()
        }
    }

    private fun report(r: Importer.Report, request: ImportRequest): ImportReport {
        val fr = request.lang.startsWith("fr")
        fun l(en: String, frText: String) = if (fr) frText else en
        val b = ImportReport.builder(r.dryRun, request.batch)
            .count("listings", l("active listings", "ventes en cours"), r.listings)
            .count("item_claims", l("items to claim", "objets à récupérer"), r.itemClaims)
            .count("money_claims", l("amounts to claim", "sommes à récupérer"), r.moneyClaims)
            .count("sales", l("sales history", "historique des ventes"), r.sales)
            .alreadyImported(r.alreadyImported)
        r.unreadable.forEach { b.reject(it, "unreadable_item", l("an item this server cannot read", "un objet illisible sur ce serveur")) }
        r.rejected.forEach { b.reject(it.id, it.code, it.message) }
        if (r.unknownCurrencies.isNotEmpty()) {
            b.blocker(l("unknown currencies, map them in import.currency_map: ", "monnaies inconnues, à associer dans import.currency_map : ") +
                r.unknownCurrencies.sorted().joinToString())
        }
        r.problems.forEach(b::note)
        return b.build()
    }
}
