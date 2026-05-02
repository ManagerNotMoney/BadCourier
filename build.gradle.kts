plugins {
    id("java")
}

group = "io.github.potaseval"
version = "0.0.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("libs/GreatWeeb-0.1.0.jar"))
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}