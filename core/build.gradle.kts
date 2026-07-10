// 공유 코어 모듈: 모델(Language, Problem 등) + 디버그 어댑터 확장점.
// 메인 플러그인과 IDE별 디버거 모듈(:debugger-go 등)이 모두 이 모듈에 의존한다.
// 플랫폼 공통 API만 사용하므로 어떤 JetBrains IDE SDK로도 컴파일 가능 — 기준은 IC.
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
        intellijIdeaCommunity("2024.3")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}
