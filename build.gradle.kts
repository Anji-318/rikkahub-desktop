import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "me.rerere.rikkahub"
version = "0.6.0"

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

// 生成与 version 联动的版本号常量（标题栏 / 设置页显示用，避免硬编码漂移）
// 注意：必须用独立 task 类，脚本内联 doLast 闭包会捕获脚本对象，与 configuration cache 不兼容
abstract class GenerateAppVersionTask : DefaultTask() {
    @get:Input
    abstract val appVersion: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val f = outputFile.get().asFile
        f.parentFile.mkdirs()
        f.writeText(
            """
            package me.rerere.rikkahub.desktop

            /** 由构建生成，与 build.gradle.kts 的 version 联动，请勿手动修改 */
            const val APP_VERSION: String = "${appVersion.get()}"
            """.trimIndent() + "\n"
        )
    }
}

val generateAppVersion = tasks.register<GenerateAppVersionTask>("generateAppVersion") {
    appVersion.set(project.version.toString())
    outputFile.set(layout.buildDirectory.file("generated/appVersion/me/rerere/rikkahub/desktop/AppVersion.kt"))
}

sourceSets.main {
    kotlin.srcDir(layout.buildDirectory.dir("generated/appVersion"))
}

tasks.named("compileKotlin") {
    dependsOn(generateAppVersion)
}

compose.desktop {
    application {
        mainClass = "me.rerere.rikkahub.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "RikkaHub"
            packageVersion = "0.6.0"
            description = "RikkaHub Desktop - LLM Chat Client"
            vendor = "rikkahub"
            windows {
                iconFile.set(project.file("icon.ico"))
            }
            macOS {
                // jpackage macOS 校验 app-version 主版本号必须 > 0，0.x 版本单独指定
                // （app 镜像和 DMG 都要覆盖，否则 createDistributable 失败）
                packageVersion = "1.0.0"
                dmgPackageVersion = "1.0.0"
            }
        }
    }
}

tasks.shadowJar {
    archiveBaseName.set("rikkahub-desktop")
    archiveVersion.set("0.6.0")
    mergeServiceFiles()
    manifest {
        attributes("Main-Class" to "me.rerere.rikkahub.desktop.MainKt")
    }
}
