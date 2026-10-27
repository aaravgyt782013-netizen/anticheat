plugins { java }
group = "dev.annamc"
version = "0.1.0-SNAPSHOT"
val paperApiVersion = providers.gradleProperty("paperApiVersion")
    .orElse("1.21-R0.1-SNAPSHOT").get()
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.yaml:snakeyaml:2.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
tasks.jar { archiveBaseName.set("ANNA-AC") }
