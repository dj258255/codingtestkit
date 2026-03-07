<p align="center">
  <img src="ctk-pixel.svg" width="128" alt="CodingTestKit Icon"/>
</p>

# CodingTestKit - 알고리즘 문제 풀이 올인원 플러그인

IntelliJ IDE에서 알고리즘 문제를 **가져오고**, **테스트하고**, **제출**까지 한번에 할 수 있는 플러그인입니다.

## 지원 플랫폼

| 플랫폼 | 문제 크롤링 | 로컬 테스트 | 코드 제출 |
|--------|:---------:|:---------:|:--------:|
| **백준 (BOJ)** | O | O | O |
| **프로그래머스** | O | O | O |
| **SWEA** | O | O | O |

## 주요 기능

### 문제 가져오기
- 문제 번호만 입력하면 문제 설명, 테스트 케이스 자동 추출
- 프로젝트 내 폴더 자동 생성 (`problems/플랫폼/난이도/문제명/`)
- README.md + 코드 파일 자동 생성 및 에디터에서 열기
- SWEA: input.txt/output.txt 자동 다운로드, 이미지 로컬 저장

### 자동 문제 인식
- 코드 파일이나 README를 열면 해당 문제를 자동으로 인식
- 문제 정보, 테스트 케이스가 자동으로 로드

### 로컬 테스트 실행
- Java, Python, C++, Kotlin 지원
- 백준/SWEA: stdin 입력 방식
- 프로그래머스: solution 함수 자동 래핑 실행
- 디버그 출력 자동 분리 (`System.out.println` 등)
- 아코디언 스타일 테스트 케이스 UI (접기/펼치기)

### 코드 제출
- JCEF 내장 브라우저로 각 플랫폼에 직접 제출
- 코드와 언어 자동 입력
- 수동 입력 버튼 제공 (네트워크 환경에 따른 대비)
- 제출 결과를 브라우저에서 바로 확인 가능

### 코드 템플릿
- 자주 쓰는 코드 템플릿 저장 및 불러오기
- 언어별 기본 템플릿 제공

### 시험 환경 모드
- 코드 자동완성 끄기/켜기
- 코드 검사(Inspections) 끄기/켜기
- 실제 코딩테스트 시험 환경과 동일한 조건에서 연습 가능

### 타이머
- 스톱워치 (랩 기능 포함)
- 카운트다운 타이머
- 시험 시간 관리에 활용

## 설치 방법

### JetBrains Marketplace
1. IntelliJ IDEA > Settings > Plugins > Marketplace
2. "CodingTestKit" 검색 후 설치

### 수동 설치
1. [Releases](https://github.com/dj258255/codingtestkit/releases)에서 `.zip` 파일 다운로드
2. IntelliJ IDEA > Settings > Plugins > 톱니바퀴 > Install Plugin from Disk

## 사용법

### 1. 문제 가져오기
1. 우측 사이드바에서 **CodingTestKit** 열기
2. 플랫폼과 언어 선택
3. 문제 번호 입력 후 **가져오기** 클릭
   - 백준: `1000` (문제 번호)
   - 프로그래머스: `12947` (URL의 `/lessons/` 뒤 숫자)
   - SWEA: `1204` (문제 번호)

### 2. 로그인
1. **로그인** 버튼 클릭
2. 내장 브라우저에서 해당 플랫폼에 로그인
3. 로그인 감지 시 자동으로 쿠키 저장 및 닫힘

### 3. 테스트 실행
1. **테스트** 탭 이동
2. 코드 작성 후 **전체 실행** 클릭
3. PASS/FAIL 결과 확인 (FAIL 시 자동 펼침)

### 4. 코드 제출
1. **제출** 버튼 클릭
2. 내장 브라우저에서 코드 자동 입력 확인
3. 제출 버튼 클릭 후 결과 확인

## 지원 언어

| 언어 | 백준 | 프로그래머스 | SWEA |
|------|:----:|:----------:|:----:|
| Java | O | O | O |
| Python | O | O | O |
| C++ | O | O | O |
| Kotlin | O | O | X |

## 요구 사항

- IntelliJ IDEA 2024.1 이상
- JDK 17 이상 (Java 실행용)
- 각 언어 컴파일러 (해당 언어 테스트 시)

## 빌드

```bash
./gradlew buildPlugin
```

빌드된 플러그인은 `build/distributions/` 폴더에 생성됩니다.

## 라이선스

MIT License

## 제작자

- **dj258255** - [GitHub](https://github.com/dj258255)
