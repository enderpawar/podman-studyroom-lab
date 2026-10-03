# podman-studyroom-lab

스터디룸 예약 API(Spring Boot 3.5.3, Java 17, MySQL 8, Flyway, JWT)를 **Docker 대신 Podman으로** 띄우기 위한 실험 저장소다.
원본 학습 저장소(`enderpawar/Developer-Roadmap_Spring_Study` @ `15e0703`)에서 예제 코드만 복사해 왔고, 학습 기록 문서는 가져오지 않았다.

> **현재 상태: 코드는 작성했고 컨테이너 실행은 미검증이다.**
> 작성한 PC(Windows 11)에 Podman과 WSL이 없어서, 실제로 돌려 본 것은 Gradle 테스트와 Testcontainers 이미지 이름 검사뿐이다.
> 무엇을 확인했고 무엇을 확인하지 못했는지는 [docs/verification.md](docs/verification.md)에 원문 그대로 남겼다.

## 1. 저장소 구조

```text
app/                          원본 예제 코드(Spring Boot)
  Dockerfile                  ← FROM 정식 이름, non-root(USER 1001)
  build.gradle.kts            ← Testcontainers 의존성 추가
  src/test/.../FlywayMySqlIntegrationTest.java   ← 신규
compose.yaml                  원본 그대로(podman compose로 실행)
scripts/podman-pod-up.sh      Pod 수동 구성(pod create → mysql → healthy 대기 → app)
scripts/podman-pod-down.sh    Pod 정리(--volumes 로 DB 볼륨까지)
deploy/studyroom-pod.yaml     쿠버네티스 매니페스트(PVC + Pod) — kube play / CI / Quadlet 공통
deploy/quadlet/studyroom.kube systemd 사용자 서비스용 Quadlet 유닛
.github/workflows/ci.yml      기존 test·docker 잡 + podman 잡 추가
.gitattributes                gradlew·*.sh를 LF로 고정(Windows 체크아웃 대비)
docs/verification.md          검증 기록·블로그 초안과의 차이·남은 과제
```

## 2. 커밋 순서대로 읽는 법

`main`은 원본을 가져온 상태이고, `feat/podman`에 단계별로 한 커밋씩 쌓았다. 커밋 하나가 학습 단위 하나다.

```bash
git log --oneline main..feat/podman      # 단계 목록
git show e2af262                         # 한 단계씩 diff 읽기
git diff main feat/podman --stat         # Podman 전환으로 바뀐 파일 전체
```

| 순서 | 커밋 | 내용 | 볼 개념 |
|---|---|---|---|
| 1 | `build: qualify Dockerfile base images` | `FROM docker.io/library/...` | short name 해석과 `registries.conf` |
| 2 | `feat: add scripts to run app and mysql in a single podman pod` | Pod 수동 구성 | Infra 컨테이너, 네트워크 네임스페이스 공유 → `localhost:3306` |
| 3 | `feat: add kubernetes pod manifest` | PVC + Pod YAML | `depends_on`이 없는 쿠버네티스에서 `restartPolicy`에 기대는 기동 순서 |
| 4 | `build: run the app image as non-root uid 1001` | `USER 1001` | rootless(호스트 쪽)와 컨테이너 안 non-root(이미지 쪽)의 구분 |
| 5 | `feat: add quadlet unit` | `.kube` 유닛 | systemd generator, linger |
| 6 | `ci: add podman job` | kube play로 CI 검증 | 로컬과 CI가 같은 YAML 하나를 기준으로 삼는 구조 |
| 7 | `test: add Testcontainers MySQL test` | 실제 MySQL로 Flyway 검증 | `@ServiceConnection`, H2 호환 모드의 한계(Day32) |

## 3. 핵심 차이 한 장 요약

```text
Compose  : [mysql 컨테이너] ←─ mysql:3306 (서비스 이름 DNS) ─── [app 컨테이너]
           호스트에 3306, 8080 둘 다 publish

Pod      : ┌──────────── Pod studyroom (Infra 컨테이너가 네트워크 네임스페이스 소유) ────────────┐
           │ [studyroom-mysql] ←── localhost:3306 ─── [studyroom-app]                             │
           └──────────────────────────────────── 8080만 호스트로 publish ─────────────────────────┘
           app의 DB 주소는 SPRING_DATASOURCE_URL 환경변수로 덮어씀(환경변수 > application-docker.yml)
```

## 4. 실행 순서 (Podman 설치 후)

### 4.1 설치 — Windows

```powershell
wsl --install --no-distribution        # 관리자 권한, 재부팅이 필요할 수 있음
winget install -e --id RedHat.Podman
podman machine init
podman machine start                   # 출력의 "API forwarding listening on: npipe:..." 줄을 기록해 둔다
podman version
podman info --format '{{.Host.Security.Rootless}}'
```

셸 스크립트(`scripts/*.sh`)는 **Git Bash**에서 실행한다. Git Bash에서는 GNU `timeout`이 Windows `timeout.exe`보다 먼저 잡힌다(`which -a timeout`으로 확인함). 스크립트는 PowerShell에서 실행하지 않는다.

### 4.2 단계별 명령

```bash
# 1) 이미지 빌드
time podman build -t localhost/study-room-api:dev ./app
podman images localhost/study-room-api

# 2) 기존 compose.yaml 그대로
podman compose up -d          # 첫 줄에 어떤 provider(docker-compose / podman-compose)를 부르는지 출력된다
curl -f http://localhost:8080/health
podman compose logs | grep -E "mysql.*healthy|Started StudyRoomApiApplication"   # 기동 순서 확인
podman compose down -v

# 3) Pod 수동 구성
./scripts/podman-pod-up.sh
podman ps -a --pod            # studyroom-infra 포함 3개
curl -f http://localhost:8080/health
# 호스트 3306이 닫혀 있는지(PowerShell): Test-NetConnection localhost -Port 3306

# 4) kube generate 비교 → kube play
podman kube generate studyroom -f /tmp/generated.yaml   # deploy/ 파일을 덮어쓰지 말고 따로 뽑아 비교
./scripts/podman-pod-down.sh
podman kube play deploy/studyroom-pod.yaml
podman ps -a --pod            # 이름이 studyroom-mysql / studyroom-app 인지
podman logs studyroom-app | grep -cE "Communications link failure|Connection refused"   # mysql보다 먼저 떠서 실패한 횟수
podman inspect studyroom-app --format '{{.RestartCount}}'
curl -f http://localhost:8080/health
podman exec studyroom-app id  # 5) non-root 확인: uid=1001
podman kube down deploy/studyroom-pod.yaml
```

### 4.3 Quadlet (리눅스 서버에서)

```bash
mkdir -p ~/.config/containers/systemd
cp deploy/quadlet/studyroom.kube deploy/studyroom-pod.yaml ~/.config/containers/systemd/
/usr/libexec/podman/quadlet -dryrun -user      # 생성될 studyroom.service 미리보기
systemctl --user daemon-reload
systemctl --user start studyroom
systemctl --user status studyroom
curl -f http://localhost:8080/health
systemctl --user stop studyroom
```

Windows의 `podman machine` 안에서도 `podman machine ssh`로 들어가 해 볼 수는 있다. 다만 그 VM의 systemd 사용자 세션이 서버 환경과 같다고 가정하지 않는다.

## 5. Testcontainers ↔ Podman

```bash
# 컨테이너 엔진이 없어도 이 명령은 성공한다(테스트 2개 skip). 기존 71개도 그대로 통과.
./gradlew test
# Testcontainers 테스트만
./gradlew test --tests '*FlywayMySqlIntegrationTest'
```

연결 설정은 빌드 파일에 넣지 않고 환경변수로 준다. 아래는 Testcontainers 공식 문서와 블로그 초안의 설정이다. **이 저장소에서는 어느 것도 실행해 보지 못했다.**

```bash
# Linux (rootless)
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix://${XDG_RUNTIME_DIR}/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true

# macOS (podman machine)
export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

```powershell
# Windows (podman machine) — 미검증. podman machine start 출력의 npipe 주소를 쓴다.
$env:DOCKER_HOST = 'npipe:////./pipe/podman-machine-default'
$env:TESTCONTAINERS_RYUK_DISABLED = 'true'
```

엔진이 연결되면 확인할 두 가지:

1. `dataSourceIsMySqlNotH2` 통과 → Test 태스크가 강제한 H2 `SPRING_DATASOURCE_URL`을 `@ServiceConnection`이 덮어썼다는 증거
2. Day32 재현: `app/src/main/resources/db/migration/V999__tmp.sql`에 `--공백없는주석` 한 줄과 `select 1;`을 넣는다. 이 테스트만 MySQL 1064로 실패하고 H2 기존 테스트는 통과하는지 본다. **확인 후 파일은 반드시 지운다.**
