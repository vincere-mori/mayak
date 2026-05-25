plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("app.beacon.desktop.BeaconDesktopKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    implementation("com.formdev:flatlaf:3.6")

    testImplementation(kotlin("test"))
}
