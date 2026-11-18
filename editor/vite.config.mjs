import { artisanModuleBundle } from "@artisan/editor-sdk/bundle";
import { defineConfig } from "vite";

// One ES module the Artisan editor imports at runtime; ArtisanAuctions registers it at startup
// (AuctionsModule.statistics, EditorBundleDeclaration.fromResource(…, "artisan-editor/ui.js", …)).
export default defineConfig(
  artisanModuleBundle({
    entry: "src/index.ts",
    outDir: "../plugin/src/main/resources/artisan-editor",
    root: import.meta.dirname,
  }),
);
