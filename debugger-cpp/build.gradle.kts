// CLion 디버거 어댑터 모듈 (이슈 #36 Tier 3).
// CLion SDK로 컴파일된다 — CLionExternalRunConfiguration(Custom Build Application) 등이
// CLion 플러그인(com.intellij.clion)에 있기 때문. 사용자 IDE(2026.1)와 맞춰 컴파일하며,
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
        // 어댑터는 구성 타입을 ID로 찾고 리플렉션으로 설정하므로 CLion 클래스 직접 참조가 없다.
        // (CppFileRunConfiguration은 V2 콘텐츠 모듈이라 컴파일해도 런타임 클래스로더가 격리됨)
        clion("2026.1.4")
    }
    compileOnly(project(":core"))
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
