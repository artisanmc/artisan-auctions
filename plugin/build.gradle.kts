import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    id("io.github.goooler.shadow") version "8.1.8"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
}

dependencies {
    compileOnly(rootProject.extra["paperApi"] as String)
    compileOnly(rootProject.extra["artisanApi"] as String)
    compileOnly(rootProject.extra["vaultApi"] as String) { isTransitive = false }
    // Not shaded: Artisan (a hard `depend`) ships kotlin-stdlib 2.0.21 under its
    // own names and Paper's legacy classloader shares it. A second, relocated copy
    // would break any Kotlin default argument of the core API (the synthetic
    // constructors take kotlin.jvm.internal.DefaultConstructorMarker). Keep this
    // version equal to Artisan's.
    compileOnly(kotlin("stdlib", "2.0.21"))
    implementation(project(":api"))

    testImplementation(kotlin("stdlib", "2.0.21"))
    testImplementation(kotlin("test"))
    testImplementation(rootProject.extra["paperApi"] as String)
    testImplementation(rootProject.extra["artisanApi"] as String)
    testImplementation("com.h2database:h2:2.3.232")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.shadowJar {
    archiveBaseName.set("ArtisanAuctions")
    archiveClassifier.set("")
}

tasks.build { dependsOn(tasks.shadowJar) }
