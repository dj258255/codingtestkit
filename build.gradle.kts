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
        // 공유 코어 모듈(모델 + 디버그 어댑터 EP)과 IDE별 디버거 모듈 — 메인 jar에 병합된다 (이슈 #36).
        // 각 디버거 모듈은 자기 IDE SDK로 컴파일되지만, 클래스 로드는 optional 의존성이 제어하므로
        // 해당 플러그인이 없는 IDE에서는 그 클래스가 로드되지 않는다.
        pluginModule(implementation(project(":core")))
        pluginModule(implementation(project(":debugger-go")))
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

// 로컬 설치된 각 JetBrains IDE에서 플러그인을 실검증하는 샌드박스 실행 태스크 (이슈 #36).
// 예: ./gradlew runGoLand → 플러그인이 설치된 GoLand 샌드박스 실행 (Go 디버깅 검증용).
// 해당 IDE가 설치돼 있지 않으면 그 태스크만 실패하고 빌드에는 영향 없다.
intellijPlatformTesting {
    runIde {
        val home = System.getProperty("user.home")
        register("runGoLand") { localPath = file("$home/Applications/GoLand.app") }
        register("runPyCharm") { localPath = file("$home/Applications/PyCharm.app") }
        register("runCLion") { localPath = file("$home/Applications/CLion.app") }
        register("runRustRover") { localPath = file("$home/Applications/RustRover.app") }
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
