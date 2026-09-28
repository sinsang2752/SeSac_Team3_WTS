# ADR-0001. Java 21을 Gradle Toolchain 자동 프로비저닝으로 확보한다

- 상태: 채택
- 일자: 2026-09-21
- 관련: CLAUDE.md §4 (확정 기술 스택), §54 (재현 가능한 빌드)

## 맥락

CLAUDE.md는 Java 21을 요구하지만 개발 머신에는 JDK 19와 11만 설치되어 있었다.
3인 팀이므로 "각자 JDK 21을 설치한다"는 온보딩 단계에서 버전이 어긋나기 쉽다.

## 결정

`settings.gradle`에 `foojay-resolver-convention` 플러그인을 적용하고
루트 `build.gradle`에서 toolchain을 21로 고정한다.

```groovy
java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}
```

Gradle 데몬 자체는 로컬에 설치된 아무 JDK(17 이상)로 돌고,
컴파일·테스트·실행만 Gradle이 내려받은 JDK 21로 수행한다.

## 결과

**좋은 점**

- 시스템에 JDK를 설치하지 않아도 된다. `./gradlew`만 있으면 된다.
- 팀원과 CI가 동일한 JDK 21로 빌드한다. "내 컴퓨터에서는 되는데" 문제가 줄어든다.

**감수하는 점**

- 최초 빌드에서 JDK(약 200MB)를 한 번 내려받는다. 오프라인 환경에서는 실패한다.
- IDE는 toolchain을 인식하지만, 일부 IDE는 프로젝트 SDK를 수동 지정해야 할 수 있다.

## 대안

`brew install openjdk@21` 로 시스템에 설치하는 방법도 검토했다.
IDE 연동이 단순해지지만 팀원별 설치 상태가 제각각이 될 수 있고,
개발 머신의 전역 환경을 바꾸게 되어 채택하지 않았다.
