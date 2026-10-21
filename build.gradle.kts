allprojects {
    group = "net.artisanmc"
    version = providers.gradleProperty("auctionsVersion").getOrElse("0.1.0-SNAPSHOT")

    repositories {
        mavenLocal() // artisan-core-api before it is released: `cd artisan/plugin && ./gradlew :api:publishToMavenLocal -PapiVersion=2.10.0`
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.artisanmc.net/releases")
        maven("https://repo.artisanmc.net/snapshots")
        maven("https://jitpack.io") // VaultAPI
    }
}

// Same versions as the Artisan core build: one place to bump them.
extra["paperApi"] = "io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT"
extra["artisanApi"] = "net.artisanmc:artisan-core-api:2.10.0"
extra["vaultApi"] = "com.github.MilkBowl:VaultAPI:1.7.1"
