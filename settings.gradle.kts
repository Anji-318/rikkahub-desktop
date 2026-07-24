// CI（GitHub 等境外 runner）访问阿里云镜像不稳定（偶发 502 且 Gradle 不会回退），
// CI 下完全走官方仓库；本地开发（国内）保持阿里云镜像优先
// 注意：pluginManagement 块独立求值，isCi 需在各自块内声明
pluginManagement {
    val isCi = System.getenv("CI") == "true"
    repositories {
        if (!isCi) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    val isCi = System.getenv("CI") == "true"
    repositories {
        if (!isCi) {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
        }
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "rikkahub-desktop"
