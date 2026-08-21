// PyCharm 디버거 어댑터 모듈 (이슈 #36 Tier 2).
// PyCharm SDK로 컴파일된다 — PyRemoteDebugConfigurationType 등이 Python 플러그인(Pythonid)에
// 있기 때문. 2025.x에서 패키지가 com.intellij.python.pro.*로 이동해 2026.1 기준으로 컴파일하며,
// 구버전 PyCharm에서는 어댑터 로드가 실패해도 isAvailable=false로 우아하게 미지원 처리된다.
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
        pycharmProfessional("2026.1.4")
        // 일반 Python 실행 구성(PythonConfigurationType)은 PythonCore에 있음 —
        // Community에도 있어 무료 PyCharm까지 커버된다.
        bundledPlugin("PythonCore")
        // 실제 PyCharm 플랫폼에서 어댑터 배선을 검증하기 위한 헤드리스 테스트 (이슈 #36).
        // 이 어댑터는 PyCharm API에 이름으로 접근하는 부분이 있어, 컴파일만으로는
        // 런타임에 그 이름이 실제로 존재하는지 알 수 없다.
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    compileOnly(project(":core"))
    testImplementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // 플랫폼 테스트는 JUnit3/4 계열
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
    // 2026.1 SDK는 Kotlin 2.3 메타데이터 — 1.9 컴파일러로 읽기 위해 검사 스킵.
    // 어댑터가 호출하는 API(PyRemoteDebugConfiguration 등)는 전부 Java 클래스라 안전.
    kotlinOptions.freeCompilerArgs += "-Xskip-metadata-version-check"
}
