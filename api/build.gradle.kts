// artisan-auctions-api: what other plugins compile against (events, service).
// Pure Java on purpose, like Artisan's modules.api: a plugin that depends on it
// never has to resolve a kotlin.* type across plugin classloaders.
plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

dependencies {
    compileOnly(rootProject.extra["paperApi"] as String)
    // Only for @Beta / @Stable.
    compileOnly(rootProject.extra["artisanApi"] as String)
}

publishing {
    publications {
        create<MavenPublication>("auctionsApi") {
            artifactId = "artisan-auctions-api"
            from(components["java"])
            pom {
                name.set("Artisan Auctions API")
                description.set("Events and service of the Artisan Auctions add-on.")
                url.set("https://artisanmc.net")
            }
        }
    }
    repositories {
        maven {
            name = "artisan"
            val snapshot = version.toString().endsWith("SNAPSHOT")
            url = uri(if (snapshot) "https://repo.artisanmc.net/snapshots" else "https://repo.artisanmc.net/releases")
            credentials {
                username = providers.environmentVariable("ARTISAN_REPO_USER").orNull
                password = providers.environmentVariable("ARTISAN_REPO_TOKEN").orNull
            }
        }
    }
}
