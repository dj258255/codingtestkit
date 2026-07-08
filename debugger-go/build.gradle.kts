// GoLand 디버거 어댑터 모듈 (이슈 #36 Tier 3).
// GoLand SDK로 컴파일된다 — GoRemoteDebugConfigurationType 등 Go 플러그인 클래스가
// IntelliJ IDEA SDK에는 없기 때문. 결과물은 메인 플러그인 jar에 병합되고,
// 런타임 로드는 plugin.xml의 optional 의존성(org.jetbrains.plugins.go)이 제어한다.
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
        goland("2024.3")
        // Go Remote 디버그 설정 타입은 GoLand에 번들된 Go 플러그인에 있음
        bundledPlugin("org.jetbrains.plugins.go")
    }
    // 코어(어댑터 인터페이스)는 메인 플러그인이 이미 배포에 포함하므로 컴파일 전용
    compileOnly(project(":core"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}
