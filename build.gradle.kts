group = "bhawkins.nest"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "2.1.0"
    application
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")
    implementation("net.dv8tion:JDA:5.2.2")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
}

application {
    mainClass = "core.MainKt"
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
