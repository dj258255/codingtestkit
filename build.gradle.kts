plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.codingtestkit"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.google.code.gson:gson:2.10.1")

    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.codingtestkit"
        name = "CodingTestKit"
        version = "1.0.0"
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "253.*"
        }
    }

    buildSearchableOptions = false
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
