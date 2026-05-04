plugins {
    id("java")
}

group = "io.github.potaseval"
version = "0.0.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("libs/GreatWeeb-0.1.0.jar"))
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude("org.bukkit", "bukkit")
    }
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}