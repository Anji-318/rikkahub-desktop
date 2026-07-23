import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "me.rerere.rikkahub"
version = "0.4.0"

val ktorVersion = "3.1.3"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Markdown 渲染（Compose Multiplatform）
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.35.0")

    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

compose.desktop {
    application {
        mainClass = "me.rerere.rikkahub.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "RikkaHub"
            packageVersion = "0.4.0"
            description = "RikkaHub Desktop - LLM Chat Client"
            vendor = "rikkahub"
            windows {
                iconFile.set(project.file("icon.ico"))
            }
            macOS {
                // DMG 校验要求主版本号 > 0，0.x 版本单独指定
                dmgPackageVersion = "1.0.0"
            }
        }
    }
}

tasks.shadowJar {
    archiveBaseName.set("rikkahub-desktop")
    archiveVersion.set("0.4.0")
    mergeServiceFiles()
    manifest {
        attributes("Main-Class" to "me.rerere.rikkahub.desktop.MainKt")
    }
}
