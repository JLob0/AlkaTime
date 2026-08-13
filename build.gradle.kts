import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.alkacode"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    // banco/HikariCP e GUI base vem do AlkaCore (DatabaseProvider/BaseGui) - AlkaTime
    // nao abre conexao JDBC propria nem registra o proprio GuiListener.
    compileOnly("com.alkacode:AlkaCore:1.0.1")
    // moeda "ticks" das recompensas de tempo online vem da AlkaEconomy (EconomyManager).
    compileOnly("com.alkacode:AlkaEconomy:1.0.6")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Citizens nao tem artefato Maven publico confiavel - hook via reflection pura
    // (ver hook/CitizensHook), entao nao entra aqui como dependencia de compilacao.
    // DecentHolograms e o padrao ja adotado pelo AlkaMines para hologramas na rede
    // (nao HolographicDisplays) - mesma exclusao de modulos NMS que o AlkaMines usa.
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.10.1") {
        exclude(group = "com.github.decentsoftware-eu.decentholograms", module = "nms-v26_1")
        exclude(group = "com.github.decentsoftware-eu.decentholograms", module = "nms-v26_2")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    expand("version" to project.version)
}
