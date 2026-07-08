plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.codingtestkit"
version = "1.6.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        // JVM 원격 디버그(RemoteConfigurationType 등)는 Java 플러그인 모듈에 있음 (이슈 #36 Tier 1).
        // 컴파일용으로만 필요하고, 런타임 로드는 plugin.xml의 optional 의존성이 제어한다.
        bundledPlugin("com.intellij.java")
        // 공유 코어 모듈(모델 + 디버그 어댑터 EP) — 배포 zip에 core.jar로 포함된다 (이슈 #36).
        pluginModule(implementation(project(":core")))
        pluginVerifier()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.codingtestkit"
        name = "CodingTestKit"
        version = "1.6.1"
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }

    buildSearchableOptions = false

    pluginVerification {
        ides {
            val ic = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity
            ide(ic, "2024.3.7")
            ide(ic, "2025.1.7")
            ide(ic, "2025.2.6.1")
        }
        failureLevel = listOf(
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES
        )
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    test {
        useJUnitPlatform()
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }
}
