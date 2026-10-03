# Podman 적용 실험 검증 기록

작성일: 2026-10-03
대상: 블로그 초안 「Podman 정리 — 데몬 없는 컨테이너와 쿠버네티스, 그리고 예약 API에 적용해 보기」 4장
원본 코드: `enderpawar/Developer-Roadmap_Spring_Study` @ `15e0703` (Week E 완료 시점)

> **확인 범위**
> 이 PC에는 Podman도 WSL도 설치되어 있지 않다. 그래서 **컨테이너를 실제로 띄우는 단계는 하나도 실행하지 않았다.**
> 실제로 실행해서 확인한 것은 Gradle 빌드·테스트, Testcontainers 이미지 이름 검사, H2 쪽 Day32 재현, YAML 파싱, `bash -n` 문법 검사뿐이다.
> 아래 표에서 `통과`는 실행해서 본 결과이고, `미검증`은 코드만 작성한 상태다. 추측한 출력은 적지 않았다.

## 1. 환경

| 항목 | 값 | 확인 방법 |
|---|---|---|
| OS | Windows 11 Pro 10.0.26200 (build 26200) | `Get-CimInstance Win32_OperatingSystem` |
| 아키텍처 | AMD64 | `$env:PROCESSOR_ARCHITECTURE` |
| Podman | **미설치** | `Get-Command podman` 결과 없음 |
| podman-compose / docker-compose / docker / act | 미설치 | `Get-Command` 결과 없음 |
| WSL | **미설치** | 아래 원문 |
| 하이퍼바이저 | 활성(`HypervisorPresent: True`) | `Win32_ComputerSystem` |
| rootless 여부 | 확인 불가(Podman 없음) | — |
| Java | Temurin 17.0.20.1 | `java -version` |
| Gradle | 8.14.5 (wrapper) | `gradle-wrapper.properties` |
| Testcontainers | 1.21.2 (Spring Boot 3.5.3 BOM이 고름) | Gradle 캐시 `org.testcontainers/mysql/1.21.2` |
| git | `core.autocrlf=true` | `git config --get core.autocrlf` |

`wsl -l -v` 출력 원문(UTF-16으로 다시 읽음):

```text
wsl : Linux용 Windows 하위 시스템 설치되어 있지 않습니다. 'wsl.exe --install'을 실행하여 설치할 수 있습니다.
자세한 내용은 https://aka.ms/wslinstall
 참조하세요.
```

## 2. 단계별 결과 요약

| 단계 | 커밋 | 실행한 것 | 결과 | 걸린 시간 |
|---|---|---|---|---|
| 기준선 | `a8c7854` | `gradlew test` (가져온 직후) | **통과** — 71 tests, 0 failures, 0 errors | 82초(`BUILD SUCCESSFUL in 1m 21s`) |
| 0. 환경 확인 | — | 위 표 | Podman 미설치로 중단 지점 | — |
| 1. 이미지 빌드 | `e2af262` | `FROM` 정규화만 | **미검증** — `podman build` 미실행, 이미지 크기 없음 | — |
| 2. podman compose | (파일 변경 없음) | — | **미검증** — provider, `service_healthy` 순서 미확인 | — |
| 3. Pod 스크립트 | `865d14d` | `bash -n` | **문법만 통과**, 실행 미검증 | — |
| 4. kube YAML | `f807760` | snakeyaml 파싱 | **파싱만 통과**(문서 2개: PVC, Pod), `kube play` 미검증 | — |
| 5. non-root | `a42f157` | — | **미검증** — `podman exec studyroom-app id` 미확인 | — |
| 6. Quadlet | `7a0b33d` | — | **미검증** — Windows라 systemd·dryrun 불가 | — |
| 7. CI podman 잡 | `ef95acf` | snakeyaml로 jobs 구조 확인 | **구조만 통과**(`test`/`docker`/`podman`, 기존 줄 삭제 0), 실행 미검증 | — |
| 8. Testcontainers | `f623e13` | 아래 8절 | **부분 통과** — 엔진 없는 환경의 skip 동작, 이미지 이름 버그 수정. MySQL 실행은 미검증 | 전체 테스트 45초 |

## 3. 단계별 상세

### 3.1 기준선 테스트

```powershell
cd app; .\gradlew.bat test --no-daemon --console=plain
```

```text
BUILD SUCCESSFUL in 1m 21s
4 actionable tasks: 4 executed
```

JUnit XML 합계: `tests=71 skipped=0 failures=0 errors=0`

### 3.2 1단계 — Dockerfile FROM 정규화

변경: `eclipse-temurin:17-jdk` → `docker.io/library/eclipse-temurin:17-jdk`, `17-jre`도 같은 방식으로 바꿈.
short name으로 빌드했을 때 프롬프트나 오류가 나는지는 Podman이 없어서 확인하지 못했다.

**Windows에서 정적으로 발견한 문제 — `gradlew` 줄바꿈**

원본 저장소를 이 PC(`core.autocrlf=true`)에 클론하면 `.gitattributes`가 없어서 `gradlew`가 CRLF로 체크아웃된다.

```text
$ file app/gradlew
app/gradlew: POSIX shell script, Unicode text, UTF-8 text executable, with CRLF line terminators
$ head -c 20 app/gradlew | od -c
0000000   #   !   /   b   i   n   /   s   h  \r  \n  \r  \n   #  \r  \n
```

- 처음 생각: Dockerfile은 OS와 무관하게 똑같이 빌드된다.
- 실제 상황: Dockerfile 1단계는 `COPY . .` 후 리눅스 컨테이너에서 `./gradlew`를 실행한다. shebang이 `#!/bin/sh\r`가 되므로 Windows 작업 트리에서 `podman build ./app`을 하면 실패할 가능성이 높다. **빌드 실패 자체는 실행해 보지 못했다(미검증).** CI(ubuntu)에서는 LF로 체크아웃되므로 이 문제가 드러나지 않는다.
- 조치: `.gitattributes`에 `gradlew text eol=lf`, `*.sh text eol=lf` 추가(`a8c7854`). 다시 체크아웃한 뒤 `git ls-files --eol app/gradlew` 결과가 `i/lf w/lf attr/text eol=lf`인 것을 확인했다.

### 3.3 3단계 — Pod 스크립트

`scripts/podman-pod-up.sh`, `scripts/podman-pod-down.sh` (`git update-index --chmod=+x`로 실행 비트 기록)

설계에서 블로그 초안과 달라진 점:

- `podman wait --condition=healthy`에는 타임아웃 옵션이 없다. healthcheck는 systemd 타이머가 주기적으로 실행하므로, 타이머가 돌지 않는 환경에서는 상태가 계속 `starting`에 머물러 스크립트가 끝나지 않을 수 있다. 그래서 `timeout 180`으로 감싸고, 실패하면 `podman healthcheck run`을 5초마다 직접 실행하는 루프로 넘어가게 했다.
- compose.yaml에 있는 `timeout: 5s`를 `--health-timeout 5s`로 옮겼다. 초안에는 이 옵션이 빠져 있었다.
- 마지막에 `/health` 재시도 루프와 `podman ps -a --pod` 출력을 넣었다.
- 호스트 3306 차단 확인(`nc -z localhost 3306`)은 미검증이다. Git Bash에는 `nc`가 없으므로 Windows에서는 `Test-NetConnection localhost -Port 3306`을 쓴다.

### 3.4 4단계 — kube YAML

`deploy/studyroom-pod.yaml`은 블로그 설계안과 내용이 같고 주석만 추가했다. `podman kube generate` 원본 출력은 Pod를 띄우지 못해서 없다.
초안은 `podman kube generate studyroom -f deploy/studyroom-pod.yaml`로 **정리해 둔 파일을 덮어쓰는** 순서였다. 그래서 생성본은 별도 파일로 뽑아서 비교하도록 README에 적었다.

### 3.5 7단계 — CI podman 잡

기존 `test`·`docker` 잡 아래에만 47줄을 추가했다(`git diff`의 `-` 줄 0개). 이 실험 저장소에는 원격이 없고 `act`도 없어서 실행하지 않았다.
참고: 원본 `ci.yml`은 `branches: [master]`에서만 동작한다. 이 실험 저장소는 `main` 브랜치를 쓰므로 GitHub에 올리면 트리거를 조정해야 한다.

### 3.6 8단계 — Testcontainers

#### 이미지 이름 오류 (실행해서 확인)

초안 코드 `new MySQLContainer<>("docker.io/library/mysql:8")`를 커밋하지 않는 임시 테스트로 생성만 해 봤다. 컨테이너 엔진과 무관하게 **생성자에서 실패했다.**

```text
java.lang.IllegalStateException: Failed to verify that image 'docker.io/library/mysql:8' is a compatible substitute for 'mysql'. This generally means that you are trying to use an image that Testcontainers has not been designed to use. If this is deliberate, and if you are confident that the image is compatible, you should declare compatibility in code using the `asCompatibleSubstituteFor` method. For example:
   DockerImageName myImage = DockerImageName.parse("docker.io/library/mysql:8").asCompatibleSubstituteFor("mysql");
and then use `myImage` instead.
```

- 처음 생각: 정식 이름도 같은 이미지이므로 그대로 받아들일 것이다.
- 실제 원인: `MySQLContainer`는 생성자에서 이미지 이름을 기본 이름 `mysql`과 비교한다. `docker.io/library/mysql`을 같은 이름으로 보지 않는다(1.21.2).
- 수정: `DockerImageName.parse("docker.io/library/mysql:8").asCompatibleSubstituteFor("mysql")`. 같은 임시 테스트로 `tests="1" failures="0"`을 확인했고, 임시 파일은 삭제했다.
- 비교를 위해 short name `mysql:8`도 함께 넣어 봤다. 이쪽은 `ContainerFetchException: Can't get Docker image: RemoteDockerImage(imageName=mysql:8, ...)`로 실패했다. 이름 검사 때문이 아니라 출력하려고 부른 `getDockerImageName()`이 엔진에서 이미지를 조회했기 때문이다. 이 실패는 이름 검사를 통과했다는 뜻으로만 해석한다.

#### 엔진 없는 환경의 skip 동작 (실행해서 확인)

`@Testcontainers(disabledWithoutDocker = true)`를 줬다.

```powershell
.\gradlew.bat test --tests '*FlywayMySqlIntegrationTest' --no-daemon --console=plain
```

```text
BUILD SUCCESSFUL in 36s
```

```xml
<testsuite name="com.example.studyroom.FlywayMySqlIntegrationTest" tests="2" skipped="2" failures="0" errors="0" ...>
```

전체 실행 결과: `tests=73 skipped=2 failures=0 errors=0` → **기존 71개 통과, 신규 2개 skip** (45초).

#### `@ServiceConnection`과 H2 환경변수 (미검증)

`build.gradle.kts`의 Test 태스크는 `SPRING_DATASOURCE_URL=jdbc:h2:mem:...`를 강제한다. 스프링 부트의 `PropertiesJdbcConnectionDetails`는 다른 `JdbcConnectionDetails` Bean이 없을 때만 만들어지므로, `@ServiceConnection`이 등록한 Bean이 우선해야 한다. 하지만 **실제로 그런지는 컨테이너가 없어서 보지 못했다.** 그래서 테스트에 `DatabaseProductName == "MySQL"` 단언(`dataSourceIsMySqlNotH2`)을 넣어 두었다. 엔진이 있는 환경에서 이 테스트가 통과하면 덮어쓰기가 확인된다.

#### Day32 재현

임시 `app/src/main/resources/db/migration/V999__tmp.sql`(커밋하지 않음, 실행 후 삭제):

```sql
--공백없는주석
select 1;
```

| 대상 | 결과 |
|---|---|
| H2 기반 기존 테스트 | **통과** — `tests=73 skipped=2 failures=0`. 로그: `Migrating schema "PUBLIC" to version "999 - tmp"`, `Successfully applied 8 migrations to schema "PUBLIC", now at version v999` |
| FlywayMySqlIntegrationTest (MySQL 1064 기대) | **미검증** — 엔진이 없어 skip |

삭제 후 `git status --short`에 V999가 남지 않은 것을 확인했다.

## 4. 작업 중 낸 실수

- Day32 재현 첫 시도: 셸의 현재 디렉터리가 `build/test-results/test` 안이어서 `rm -rf build/test-results`가 `rm: cannot remove 'build/test-results/test': Device or resource busy`로 실패했다. `&&`로 이어 놓아서 Gradle은 실행되지 않았다. 디렉터리를 옮겨 다시 실행했고, 위 결과는 두 번째 시도의 것이다.
- 기준선 로그를 처음에 저장소 밖(`Desktop\baseline-test.log`)에 썼다가 임시 폴더로 옮겼다.

## 5. 블로그 초안과 달랐던 점

| 초안 위치 | 초안 내용 | 실제 결과 | 수정 제안 |
|---|---|---|---|
| 4장 8) 테스트 코드 | `new MySQLContainer<>("docker.io/library/mysql:8")` | **생성자에서 `IllegalStateException`** (1.21.2, 실행 확인) | `DockerImageName.parse(...).asCompatibleSubstituteFor("mysql")`로 고치고 이유를 한 줄 덧붙인다 |
| 4장 8) 설명 | 엔진 연결 설정만 다룸 | `disabledWithoutDocker = true`면 엔진이 없을 때 skip됨(실행 확인). 기본값(false)에서 실패하는지는 실행하지 않음 | "Podman 없는 PC·CI에서는 skip되게 한다"는 문단 추가 |
| 4장 8) 설명 | "`@ServiceConnection`이 H2 환경변수보다 우선한다"고 단정 | 런타임 미검증 | 검증 전까지 "공식 문서상 우선한다"로 쓰고, DB 제품명 단언을 함께 소개 |
| 4장 1) 설치 | Windows는 한 줄 링크만 있음 | `core.autocrlf=true`이고 `.gitattributes`가 없으면 `gradlew`가 CRLF로 체크아웃됨(파일 확인). Windows에서 빌드가 실패할 가능성(미검증) | Windows 절에 `.gitattributes` 항목 추가 |
| 4장 8) 소켓 설정 | Linux·macOS만 있음 | Windows(`podman machine`)는 named pipe 방식 | Windows 설정 추가(README의 미검증 명령 참고) |
| 4장 4) `podman wait` | `podman wait --condition=healthy`만 사용 | 타임아웃이 없고 healthcheck 타이머에 의존함(문서 기준 판단, 미검증) | `timeout`으로 감싸고 대체 루프를 소개 |
| 4장 4) healthcheck | `--health-timeout` 없음 | compose.yaml에는 `timeout: 5s`가 있음 | "그대로 옮김"이라고 쓰려면 `--health-timeout 5s`를 추가 |
| 4장 5) generate | `podman kube generate studyroom -f deploy/studyroom-pod.yaml` | 정리한 파일을 덮어쓰는 순서 | `-f /tmp/generated.yaml`로 뽑아서 비교·정리한다고 수정 |
| 4장 전반 | 실행 결과처럼 보이는 출력(`podman ps` 예시 등) | 이번 실험에서는 하나도 재현하지 못함 | Podman 설치 후 실제 출력으로 교체하거나 "예시 출력"이라고 표시 |

## 6. 남은 기술부채

1. Podman 설치(WSL2 + `podman machine`) 후 1~7단계를 실제로 실행하고 이 문서의 `미검증` 칸 채우기
2. `FlywayMySqlIntegrationTest`를 엔진이 있는 환경에서 실행: `dataSourceIsMySqlNotH2` 통과 여부, Day32 V999의 MySQL 1064 오류 원문
3. Windows에서 Testcontainers ↔ Podman 연결(`DOCKER_HOST`, Ryuk) 실제 필요 설정 확인
4. `.gitattributes` 수정을 원본 저장소에 반영할지 결정
5. kube YAML의 평문 비밀값 → `kind: Secret` 분리
6. 이 저장소를 GitHub에 올린다면 `ci.yml` 트리거 브랜치(`master` → `main`) 조정
