// RustRover 디버거 어댑터 모듈 (이슈 #36 Tier 3).
// RustRover SDK로 컴파일된다 — CargoCommandConfiguration 등이 Rust 플러그인
// (com.jetbrains.rust)에 있기 때문. 사용자 IDE(2026.1)와 맞춰 컴파일하며,
// 구버전에서는 isAvailable=false로 우아하게 미지원 처리된다.
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        rustRover("2026.1.4")
        bundledPlugin("com.jetbrains.rust")
        // 실제 IDE 플랫폼에서 어댑터 배선 검증 (이슈 #36) — 컴파일만으로는
        // 런타임에 그 API가 실제로 존재하는지 알 수 없다.
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    compileOnly(project(":core"))
    testImplementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
    // 2026.1 SDK는 Kotlin 2.3 메타데이터 — 1.9 컴파일러로 읽기 위해 검사 스킵
    kotlinOptions.freeCompilerArgs += "-Xskip-metadata-version-check"
}
