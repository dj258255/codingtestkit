rootProject.name = "CodingTestKit"

// IDE별 디버거 어댑터를 위한 멀티모듈 구성 (이슈 #36).
// :core       — 공유 모델(Language 등) + 디버그 어댑터 확장점. 모든 모듈의 공통 기반.
// 루트(메인)  — 플러그인 본체. IntelliJ IDEA SDK로 컴파일 (Java/Kotlin 디버거 포함).
// 이후 :debugger-go(GoLand SDK), :debugger-python(PyCharm SDK) 등이 여기에 추가된다.
include(":core")
include(":debugger-go")
include(":debugger-python")
