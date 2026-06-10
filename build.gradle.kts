group = "bhawkins.nest"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "2.3.10"
    application
    id("org.graalvm.buildtools.native") version "0.10.5"
}

repositories {
    mavenCentral()
    google()
}

val trixnityVersion = "5.6.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")
    implementation("net.dv8tion:JDA:5.2.2")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    implementation(platform("de.connect2x.trixnity:trixnity-bom:$trixnityVersion"))
    implementation("de.connect2x.trixnity:trixnity-clientserverapi-client")
    implementation("io.ktor:ktor-client-java:3.4.0")
    implementation("de.connect2x.lognity:lognity-core:2.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

application {
    mainClass = "core.MainKt"
}

tasks.test {
    useJUnitPlatform()
}

val fatJar = tasks.register<Jar>("fatJar") {
    dependsOn(tasks.jar)
    group = "distribution"
    archiveClassifier = "fat"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = application.mainClass
    }

    from(
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    )
    from(zipTree(tasks.jar.get().archiveFile))

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

kotlin {
    jvmToolchain(21)
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("watney4")
            mainClass.set(application.mainClass)
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+AddAllCharsets")
            buildArgs.add("-H:EnableURLProtocols=http,https,jar")
            buildArgs.add("--enable-all-security-services")
            buildArgs.add("--features=org.sqlite.nativeimage.SqliteJdbcFeature")
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}
