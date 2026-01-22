plugins {
    java
    id("com.gradleup.shadow") version "9.3.0"
}

group = "de.hytale-server"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))

    // 3. Testing
    implementation("com.google.guava:guava:33.0.0-jre")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.shadowJar {
    archiveClassifier.set("")

    manifest {
        attributes("Main-Class" to "de.hytale-server.plugins.ServerlistAuthenticator")
    }

    relocate("com.zaxxer.hikari", "de.hytale_server.plugins.lib.hikari")
    relocate("org.postgresql", "de.hytale_server.plugins.lib.postgresql")
    relocate("org.slf4j", "de.hytale_server.plugins.lib.slf4j")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("manifest.json") {
        expand("version" to project.version)
    }
}