# Podman 적용 실험 검증 기록

작성일: 2026-10-03
대상: 블로그 초안 「Podman 정리 — 데몬 없는 컨테이너와 쿠버네티스, 그리고 예약 API에 적용해 보기」 4장
원본 코드: `enderpawar/Developer-Roadmap_Spring_Study` @ `15e0703` (Week E 완료 시점)

> **확인 범위**
> Windows 11에 WSL2와 Podman 5.8.3을 설치하고 `podman machine`(rootless) 위에서 0~5단계와 8단계를 실제로 실행했다.
> 6단계 Quadlet은 machine VM 안의 `~/.config/containers/systemd/`에 설치해 start → status → /health → stop까지 확인했고, 끝난 뒤 유닛 파일을 지웠다.
> 7단계 CI 잡의 GitHub 러너 결과는 3.7절에 적는다.
> 출력은 실행 로그에서 그대로 발췌했다. 길어서 줄인 곳은 `...`로 표시했다.

## 1. 실행 환경

| 항목 | 값 | 확인 방법 |
|---|---|---|
| OS | Windows 11 Pro 10.0.26200 (build 26200) | `Get-CimInstance Win32_OperatingSystem` |
| 아키텍처 | AMD64 | `$env:PROCESSOR_ARCHITECTURE` |
| WSL | 2.7.13.0, 커널 6.18.33.2-2, 기본 버전 2 | `wsl --version`, `wsl --status` |
| Podman 클라이언트 | 5.8.3 (windows/amd64) | `podman version` |
| Podman 서버(machine) | 5.8.8 (linux/amd64), Fedora 44, cgroup v2, crun | `podman version`, `podman info` |
| machine | `podman-machine-default`, WSL, CPU 8, 메모리 2GiB, 디스크 100GiB | `podman machine list` |
| rootless | `true` | `podman info --format '{{.Host.Security.Rootless}}'` |
| compose provider | docker-compose v5.6.0 (임시 폴더에 단일 exe로 받음, 시스템 미설치) | `docker-compose version` |
| Java | Temurin 17.0.20.1 | `java -version` |
| Gradle | 8.14.5 (wrapper) | `gradle-wrapper.properties` |
| Testcontainers | 1.21.2 (Spring Boot 3.5.3 BOM), Ryuk 0.12.0 | Gradle 캐시, `podman images` |
| MySQL | `mysql:8` 태그 → MySQL 8.4 | Testcontainers 로그 `Database: jdbc:mysql://localhost:35785/test (MySQL 8.4)` |
| git | `core.autocrlf=true` | `git config --get core.autocrlf` |

### 1.1 설치 과정

| 순서 | 명령 | 결과 |
|---|---|---|
| 1 | `wsl --install --no-distribution` | 실패. Windows에 기본으로 있는 `wsl.exe`는 WSL이 없을 때 같은 안내만 출력함: `Linux용 Windows 하위 시스템 설치되어 있지 않습니다. 'wsl.exe --install'을 실행하여 설치할 수 있습니다.` |
| 2 | `winget install -e --id Microsoft.WSL` (일반 권한) | 실패: `Installer failed with exit code: 0x80073d28 : The package installation failed because administrator privileges are required.` |
| 3 | 같은 명령을 관리자 권한(UAC)으로 실행 | 성공: `Successfully installed`. 안내에 재부팅이 필요할 수 있다고 나왔지만 재부팅 없이 동작함 |
| 4 | `winget install -e --id RedHat.Podman` | 성공(5.8.3). 현재 셸 PATH에는 바로 반영되지 않아 `C:\Program Files\RedHat\Podman`을 직접 추가함 |
| 5 | `podman machine init` | 성공(43초). `quay.io/podman/machine-os:5.8` 이미지를 받아 WSL로 가져옴 |
| 6 | `podman machine start` | 성공. `API forwarding listening on: npipe:////./pipe/docker_engine` / `Docker API clients default to this address. You do not need to set DOCKER_HOST.` |

## 2. 단계별 결과 요약

| 단계 | 커밋 | 결과 | 걸린 시간 |
|---|---|---|---|
| 기준선(Podman 없음) | `a8c7854` | **통과**: 71 tests, 0 failures | 82초 |
| 1. 이미지 빌드 | `e2af262` | **통과**: 이미지 380 MB | 64초 |
| 1-보충. CRLF `gradlew` | (`.gitattributes`, `a8c7854`) | **원본을 그대로 빌드하면 실패함을 확인** (exit 127) | — |
| 2. podman compose | (파일 변경 없음) | **통과**: provider는 docker-compose, mysql healthy 이후 app 시작, 재시작 0 | 첫 실행 120초(이미지 pull·빌드 포함) |
| 3. Pod 스크립트 | `865d14d` | **통과**: 첫 시도 성공, 호스트 3306 닫힘 | 29초 |
| 4. kube generate / play | `f807760` | **통과**: 기존 볼륨이면 재시작 0회, 새 볼륨이면 3회 재시작 후 healthy | 9초 / 20초 |
| 5. non-root | `a42f157` → `4019dfc` | **통과(수정 1회)**: 처음에는 `gid=999`, 수정 후 `uid=1001(spring) gid=1001(spring)` | 빌드 91초, 기동 9초 |
| 5-보충. compose 재확인 | — | **통과**: 최종 Dockerfile로 compose up, `/health` OK | 79초 |
| 6. Quadlet | `7a0b33d`, `94a1c9a` | **통과**: dry-run → 설치 → start → `/health` OK(호스트·VM 모두) → stop | start 0.9초, health 확인까지 약 15초 |
| 7. CI podman 잡 | `ef95acf` | **로컬에서 같은 명령 통과**, GitHub 러너는 **미검증** | — |
| 8. Testcontainers | `f623e13` | **통과**: 설정 없이 Podman에 연결, `@ServiceConnection`이 H2를 덮어씀, Day32 1064 재현 | 전체 56초(MySQL 컨테이너 기동 15.7초) |

## 3. 단계별 상세

### 3.1 1단계 — 이미지 빌드

#### short name 해석 동작

machine 안의 설정은 아래와 같다.

```text
/etc/containers/registries.conf:unqualified-search-registries = ["registry.fedoraproject.org", "registry.access.redhat.com", "docker.io"]
/etc/containers/registries.conf:short-name-mode = "enforcing"
/etc/containers/registries.conf.d/999-podman-machine.conf:unqualified-search-registries=["docker.io"]
```

`podman pull eclipse-temurin:17-jre`(TTY 없이)는 프롬프트도 오류도 없이 성공했다.

```text
Resolving "eclipse-temurin" using unqualified-search registries (/etc/containers/registries.conf.d/999-podman-machine.conf)
Trying to pull docker.io/library/eclipse-temurin:17-jre...
```

**podman machine에서는 short name이 문제가 되지 않았다.** `999-podman-machine.conf`가 검색 레지스트리를 `docker.io` 하나로 덮어쓰기 때문이다. 다른 리눅스 배포판이나 GitHub 러너에서의 동작은 확인하지 않았다.

#### 정규화 Dockerfile 빌드 결과

```bash
podman build -f Dockerfile.step1 -t localhost/study-room-api:dev ./app   # e2af262 시점의 Dockerfile
```

```text
BUILD SUCCESSFUL in 49s
Successfully tagged localhost/study-room-api:dev
REPOSITORY                TAG         IMAGE ID      CREATED       SIZE
localhost/study-room-api  dev         510857b4e519  1 second ago  380 MB
```

64초 걸렸다(Gradle 빌드 49초 포함).

#### CRLF `gradlew` 빌드 실패 재현

원본 저장소를 이 PC에 클론하면 `.gitattributes`가 없어서 `gradlew`가 CRLF로 체크아웃된다(`file`: `with CRLF line terminators`). 그 폴더를 같은 Dockerfile로 빌드했다.

```text
[1/2] STEP 4/5: RUN chmod +x ./gradlew
--> 235b493352f1
[1/2] STEP 5/5: RUN ./gradlew bootJar --no-daemon -x test
/bin/sh: 1: ./gradlew: not found
Error: building at STEP "RUN ./gradlew bootJar --no-daemon -x test": while running runtime: exit status 127
```

- 처음 생각: Dockerfile은 호스트 OS와 상관없이 똑같이 빌드된다.
- 실제 원인: shebang이 `#!/bin/sh\r`가 되어 커널이 `/bin/sh\r`라는 인터프리터를 찾는다. 파일은 분명히 있는데 메시지는 `not found`라서 원인을 짐작하기 어렵다. CI(ubuntu)는 LF로 체크아웃되므로 이 문제가 드러나지 않는다.
- 수정: `.gitattributes`에 `gradlew text eol=lf`, `*.sh text eol=lf`. 이 실험 저장소에서는 같은 빌드가 성공했다.

### 3.2 2단계 — podman compose

#### compose provider 부재 오류

Podman for Windows 설치본에는 compose provider가 들어 있지 않다.

```text
Error: looking up compose provider failed
2 errors occurred:
	* exec: "docker-compose": executable file not found in %PATH%
	* exec: "podman-compose": executable file not found in %PATH%
```

docker-compose v5.6.0 단일 exe를 임시 폴더에 받아(sha256 확인) PATH 앞에 두고 다시 실행했다.

#### compose 실행 결과

```text
>>>> Executing external compose provider "...\scratchpad\bin\docker-compose.exe". Please see podman-compose(1) for how to disable this message. <<<<
 Image mysql:8 Pulling
 ...
 Container podman-studyroom-lab-mysql-1 Started
 Container podman-studyroom-lab-mysql-1 Waiting
 Container podman-studyroom-lab-mysql-1 Healthy
 Container podman-studyroom-lab-app-1 Starting
 Container podman-studyroom-lab-app-1 Started
```

| 확인 항목 | 결과 |
|---|---|
| provider | docker-compose (출력 첫 줄) |
| `service_healthy` 순서 | mysql 시작 22:15:22, app 시작 22:15:40. 출력에서도 `Healthy` 다음에 `Starting` |
| app 재시작 | `RestartCount=0` |
| Flyway | `Successfully applied 7 migrations to schema 'studyroom', now at version v7` |
| `/health` | `OK` (첫 시도) |
| compose가 빌드한 이미지 이름 | `docker.io/library/podman-studyroom-lab-app:latest` (Docker 호환 API가 `docker.io/library/`를 붙임) |
| `podman compose down -v` | 컨테이너·볼륨·네트워크 삭제, 남은 컨테이너 0 |

### 3.3 3단계 — Pod 스크립트

```text
waiting for studyroom-mysql to become healthy (max 180s)...
-1
studyroom-mysql is healthy (podman wait --condition=healthy)
...
OK
health check passed
CONTAINER ID  IMAGE                         ...  STATUS                   PORTS                                        NAMES               POD ID        PODNAME
3dea32d7312f                                ...  Up 27 seconds            0.0.0.0:8080->8080/tcp                       c171af705327-infra  c171af705327  studyroom
9f4a0cf07435  docker.io/library/mysql:8     ...  Up 27 seconds (healthy)  0.0.0.0:8080->8080/tcp, 3306/tcp, 33060/tcp  studyroom-mysql     c171af705327  studyroom
c0c2fb664dd4  localhost/study-room-api:dev  ...  Up 9 seconds             0.0.0.0:8080->8080/tcp                       studyroom-app       c171af705327  studyroom
```

- `podman wait --condition=healthy`는 5.8.8에서 지원되므로 대체 루프는 쓰이지 않았다. 출력된 `-1`은 이 명령이 내보낸 값이다.
- **infra 컨테이너 이름은 `studyroom-infra`가 아니라 `<Pod ID 앞 12자리>-infra`(`c171af705327-infra`)이다.** IMAGE 칸도 비어 있다.
- mysql의 `3306/tcp, 33060/tcp`는 이미지의 EXPOSE 정보일 뿐 publish된 포트가 아니다. Pod의 포트 바인딩은 `map[8080/tcp:[{0.0.0.0 8080}]]` 하나다.
- 호스트 포트(PowerShell `Test-NetConnection`): `3306 TcpTestSucceeded=False`, `8080 TcpTestSucceeded=True`
- 볼륨: `volume mysql-data -> /var/lib/mysql`. app 환경변수: `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/studyroom?...`

### 3.4 4단계 — kube generate / kube play

#### `podman kube generate studyroom` 원본 출력

```yaml
# Save the output of this file and use kubectl create -f to import
# it into Kubernetes.
#
# Created with podman-5.8.8
apiVersion: v1
kind: Pod
metadata:
  annotations:
    io.kubernetes.cri-o.SandboxID/studyroom-app: 3dea32d7312f8e601610d96fececa8a4816486fdf1b7f3d4a582de8fea045534
    io.kubernetes.cri-o.SandboxID/studyroom-mysql: 3dea32d7312f8e601610d96fececa8a4816486fdf1b7f3d4a582de8fea045534
  creationTimestamp: "2026-10-03T13:17:24Z"
  labels:
    app: studyroom
  name: studyroom
spec:
  containers:
  - args:
    - mysqld
    env:
    - name: MYSQL_USER
      value: studyroom
    - name: MYSQL_DATABASE
      value: studyroom
    - name: MYSQL_PASSWORD
      value: studyroom
    - name: MYSQL_ROOT_PASSWORD
      value: studyroom_root
    image: docker.io/library/mysql:8
    name: studyroom-mysql
    ports:
    - containerPort: 8080
      hostPort: 8080
    volumeMounts:
    - mountPath: /var/lib/mysql
      name: mysql-data-pvc
  - env:
    - name: MYSQL_USER
      value: studyroom
    - name: SPRING_DATASOURCE_URL
      value: jdbc:mysql://localhost:3306/studyroom?allowPublicKeyRetrieval=true&useSSL=false
    - name: MYSQL_PASSWORD
      value: studyroom
    - name: JWT_SECRET
      value: studyroom-docker-secret-key-must-be-at-least-32-bytes-long
    image: localhost/study-room-api:dev
    name: studyroom-app
  volumes:
  - name: mysql-data-pvc
    persistentVolumeClaim:
      claimName: mysql-data
```

생성본과 저장소의 `deploy/studyroom-pod.yaml`(정리본)을 비교하면 다음과 같다.

| 생성본 | 정리본 | 이유 |
|---|---|---|
| 컨테이너 이름 `studyroom-mysql`, `studyroom-app` | `mysql`, `app` | kube play가 `<pod>-<container>`로 이름을 붙이므로, 그대로 두면 `studyroom-studyroom-mysql`이 될 것으로 예상(직접 실행하지는 않음) |
| `hostPort: 8080`이 mysql 컨테이너에 붙음 | app 컨테이너에 둠 | 의미상 8080은 app 포트다. Pod 단위로 publish되므로 동작은 같음 |
| PVC 문서 없음(`claimName`만 있음) | `kind: PersistentVolumeClaim` 추가 | 볼륨이 없는 환경에서도 kube play가 볼륨을 만들게 하려고 |
| `restartPolicy` 없음, healthcheck 없음 | `restartPolicy: Always` | `podman run --restart on-failure:3`과 `--health-cmd`가 생성본에 옮겨지지 않았음 |
| `annotations`, `creationTimestamp`, `args: [mysqld]` | 삭제 | 런타임이 채운 값 |

블로그 초안의 정리본은 수정 없이 그대로 동작했다.

#### kube play — 기존 볼륨(3단계 데이터 재사용)

```text
Volumes:
mysql-data
Pod:
d76b248239d65e20132ddcd4af51faeb8c532919c8c8308069a760651ba6bd11
Containers:
913fa06c90a7...
53156198a1e6...
```

```text
health ok: OK after 9s (try 4)
d76b248239d6-infra | Up 8 seconds | studyroom
studyroom-mysql | Up 8 seconds | studyroom
studyroom-app | Up 8 seconds | studyroom
app restarts=0 policy=always
```

- 컨테이너 이름이 `<pod>-<container>` 규칙(`studyroom-mysql`, `studyroom-app`)임을 확인했다.
- 이미 초기화된 볼륨이라 mysql이 app(JVM 기동 약 7초)보다 먼저 준비됐고, 재시작은 0회였다.

#### kube down 후 볼륨 잔존

```text
Pods stopped:
d76b248239d6...
Pods removed:
d76b248239d6...
Secrets removed:
Volumes removed:
```

`Volumes removed:`가 비어 있고 `podman volume ls`에 `mysql-data`가 남았다. **`kube down`은 기본적으로 볼륨을 지우지 않는다.**

#### kube play — 새 볼륨에서의 app 선기동과 재시작

`podman volume rm mysql-data` 후 다시 실행했다.

```text
health ok: OK after 20s
app restarts=3
```

app 로그(`podman logs studyroom-app`): `Starting StudyRoomApiApplication` 4회, `Started StudyRoomApiApplication` 1회. 앞의 세 번은 아래 오류로 종료됐다.

```text
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'flywayInitializer' ...
Caused by: org.flywaydb.core.internal.exception.FlywaySqlException: Unable to obtain connection from database: Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago. The driver has not received any packets from the server.
```

**mysql 초기화 동안 app이 3번 재시작했고, 4번째 기동에서 healthy가 됐다.** `restartPolicy: Always`에 기대는 설계가 실제로 동작했다.

### 3.5 5단계 — non-root

#### 첫 시도 결과(`a42f157`)

```text
health ok: OK after 9s
uid=1001(spring) gid=999(spring) groups=999(spring)
-rw-r--r-- 1 1001 1001 63301136 Oct  3 13:12 app.jar
```

- 처음 생각: `useradd --system --uid 1001 spring`이면 그룹도 1001이 된다.
- 실제 원인: `--system`은 그룹 번호를 시스템 범위에서 자동으로 배정해서 999가 됐다. jar 파일은 `--chown=1001:1001`로 이름 없는 그룹 1001 소유가 됐다. 읽기 권한이 있어 기동에는 문제가 없었다.
- 수정(`4019dfc`): `groupadd --system --gid 1001 spring && useradd --system --uid 1001 --gid 1001 spring`, `USER 1001:1001`

#### 그룹 번호 수정 후 결과

```text
build exit=0 elapsed=91s
health ok: OK after 9s
uid=1001(spring) gid=1001(spring) groups=1001(spring)
-rw-r--r-- 1 1001 1001 63301136 Oct  3 13:20 app.jar
image User=1001:1001
```

이미지 크기는 380 MB로 변화가 없었다.

#### compose 재확인 결과(GitHub Actions docker 잡과 같은 구성)

`podman compose up -d --build`(79초) → `mysql-1 Healthy` → `app-1 Starting` → `/health` `OK`, `id`는 `uid=1001(spring) gid=1001(spring)`. `compose down -v` 후 남은 컨테이너 0개.

### 3.6 6단계 — Quadlet

`~/.config`를 건드리지 않으려고 machine의 `/tmp/quadlet-dry`에 `studyroom.kube`와 `studyroom-pod.yaml`을 복사해 dry-run만 실행했다. machine의 `systemctl --user is-system-running` 결과는 `running`이었다.

```bash
podman machine ssh "QUADLET_UNIT_DIRS=/tmp/quadlet-dry /usr/libexec/podman/quadlet -dryrun -user"
```

```text
quadlet-generator[13619]: Loading source unit file /tmp/quadlet-dry/studyroom.kube
---studyroom.service---
...
[Unit]
Wants=podman-user-wait-network-online.service
After=podman-user-wait-network-online.service
Description=Study room API (MySQL + Spring Boot pod)
...
After=network-online.target
SourcePath=/tmp/quadlet-dry/studyroom.kube
RequiresMountsFor=%t/containers

[X-Kube]
Yaml=studyroom-pod.yaml

[Install]
WantedBy=default.target

[Service]
KillMode=mixed
Environment=PODMAN_SYSTEMD_UNIT=%n
Type=notify
NotifyAccess=all
SyslogIdentifier=%N
ExecStart=/usr/bin/podman kube play --replace --service-container=true /tmp/quadlet-dry/studyroom-pod.yaml
ExecStopPost=/usr/bin/podman kube down /tmp/quadlet-dry/studyroom-pod.yaml
```

- rootless 유닛에 `podman-user-wait-network-online.service` 의존성이 자동으로 붙는다. 이전에 "미확인"으로 적었던 주석을 고쳤다(`94a1c9a`).
- 상대 경로 `Yaml=studyroom-pod.yaml`은 유닛 파일 위치 기준의 절대 경로로 풀렸다.
#### Quadlet 실제 설치와 실행 결과

`podman machine cp`로 두 파일을 VM의 `/home/user/.config/containers/systemd/`에 복사했다. VM 사용자는 `user`이고 `Linger=yes`이다. `podman machine ssh`는 PowerShell에서 실행했다.

```text
$ systemctl --user daemon-reload && systemctl --user list-unit-files studyroom.service
UNIT FILE         STATE     PRESET
studyroom.service generated -

$ time systemctl --user start studyroom
real	0m0.928s

$ systemctl --user status studyroom
● studyroom.service - Study room API (MySQL + Spring Boot pod)
     Loaded: loaded (/home/user/.config/containers/systemd/studyroom.kube; generated)
    Drop-In: /usr/lib/systemd/user/service.d
             └─10-timeout-abort.conf
     Active: active (running) since Sat 2026-10-03 22:46:45 KST; 12ms ago
   Main PID: 16239 (conmon)
     Memory: 26.8M (peak: 46.9M)
     CGroup: /user.slice/user-1000.slice/user@1000.service/app.slice/studyroom.service
             ├─16239 /usr/bin/conmon ... -n a9776745c720-service ...
             ├─16338 rootlessport
             ├─16345 rootlessport-child
             ├─16354 /usr/bin/conmon ... -n df3e5d522361-infra ...
             ├─16359 /usr/bin/conmon ... -n studyroom-mysql ...
             └─16372 /usr/bin/conmon ... -n studyroom-app ...
```

15초 뒤 확인한 결과:

```text
--- host(Windows PowerShell Invoke-WebRequest):
OK
--- inside VM:
OK (vm try 1)
a9776745c720-service | Up 25 seconds |
df3e5d522361-infra | Up 25 seconds | studyroom
studyroom-mysql | Up 25 seconds | studyroom
studyroom-app | Up 24 seconds | studyroom
restarts=0
```

- `systemctl start`는 0.9초 만에 끝난다. `Type=notify`이므로 이 시점은 컨테이너가 시작된 시점이고 Spring 기동 완료 시점이 아니다. 그래서 `/health`는 따로 확인해야 한다.
- `--service-container=true` 때문에 Pod 밖에 `<id>-service` 컨테이너가 하나 더 생긴다. systemd가 감시하는 Main PID는 이 컨테이너의 conmon이다.
- systemd가 VM 안에서 직접 띄운 Pod인데도 8080이 Windows 호스트까지 전달됐다.
- 볼륨 `mysql-data`를 재사용해서 app 재시작은 0회였다.

```text
$ systemctl --user stop studyroom      → stop-exit=0
$ systemctl --user is-active studyroom → inactive
$ podman ps -a | wc -l                  → 0
$ podman volume ls                      → mysql-data   (ExecStopPost=kube down이라 볼륨은 남음)
```

**정리:** linger가 켜져 있어서 유닛을 그대로 두면 podman machine을 켤 때마다 Pod가 자동으로 떠서 8080을 차지한다. 그래서 확인이 끝난 뒤 두 파일을 지우고 `daemon-reload`를 했다(`0 unit files listed`).

### 3.7 7단계 — CI podman 잡

잡의 명령(`podman build` → `podman kube play deploy/studyroom-pod.yaml` → `/health` 재시도 → `podman kube down`)은 4·5단계에서 로컬 5.8.8로 같은 순서로 실행해 통과했다.

이 저장소는 기본 브랜치가 `main`이라 트리거에 `main`과 `workflow_dispatch`를 추가했다. 기존 `master`는 그대로 두었다.

GitHub 러너 결과: (push 후 기록)

### 3.8 8단계 — Testcontainers

#### 이미지 이름 호환성 오류(Podman 없는 환경)

블로그 초안의 `new MySQLContainer<>("docker.io/library/mysql:8")`는 생성자에서 실패한다.

```text
java.lang.IllegalStateException: Failed to verify that image 'docker.io/library/mysql:8' is a compatible substitute for 'mysql'. This generally means that you are trying to use an image that Testcontainers has not been designed to use. If this is deliberate, and if you are confident that the image is compatible, you should declare compatibility in code using the `asCompatibleSubstituteFor` method. For example:
   DockerImageName myImage = DockerImageName.parse("docker.io/library/mysql:8").asCompatibleSubstituteFor("mysql");
and then use `myImage` instead.
```

수정: `DockerImageName.parse("docker.io/library/mysql:8").asCompatibleSubstituteFor("mysql")`

#### Podman 연결 설정과 Ryuk 동작

**환경변수를 하나도 설정하지 않고 실행했다**(`DOCKER_HOST`, `TESTCONTAINERS_RYUK_DISABLED` 모두 없음).

```powershell
.\gradlew.bat test --tests '*FlywayMySqlIntegrationTest' --no-daemon --console=plain
```

```text
tests="2" skipped="0" failures="0" errors="0"
Creating container for image: docker.io/library/mysql:8
Container docker.io/library/mysql:8 started in PT15.6736003S
Database: jdbc:mysql://localhost:35785/test (MySQL 8.4)
Successfully applied 7 migrations to schema `test`, now at version v7
```

- Testcontainers가 `npipe:////./pipe/docker_engine`(podman machine이 연 Docker 호환 API)을 기본 주소로 찾아 연결한 것으로 보인다. Testcontainers 쪽의 엔진 탐지 로그는 JUnit XML에 남지 않아 직접 확인하지 못했다.
- `podman events`에 `docker.io/testcontainers/ryuk:0.12.0 testcontainers-ryuk-...`의 start가 기록됐다. **rootless podman machine에서 Ryuk가 정상 동작했고**, 테스트 후 남은 컨테이너는 0개였다.
- `dataSourceIsMySqlNotH2` 통과: Test 태스크가 강제한 H2 `SPRING_DATASOURCE_URL`을 `@ServiceConnection`이 덮어썼다는 것이 확인됐다.

#### Day32 1064 오류 재현

임시 `V999__tmp.sql`(`--공백없는주석` + `select 1;`)을 넣고 전체 테스트를 실행한 뒤 삭제했다.

| 대상 | 결과 |
|---|---|
| H2 기반 기존 테스트 71개 | **통과**(`now at version v999`로 적용됨) |
| FlywayMySqlIntegrationTest 2개 | **실패**, 아래 원문 |

```text
Caused by: org.flywaydb.core.internal.exception.FlywayMigrateException: Script V999__tmp.sql failed
SQL State  : 42000
Error Code : 1064
Message    : You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near '--공백없는주석
Caused by: java.sql.SQLSyntaxErrorException: You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near '--공백없는주석
```

전체 집계: `tests=73 skipped=0 failures=2 errors=0`. **Day32의 1064 오류가 CI까지 가지 않고 로컬 테스트에서 재현됐다.**

#### 최종 테스트 결과

| 환경 | 결과 |
|---|---|
| Podman 없음(설치 전) | 73 = 71 passed + 2 skipped, 45초 |
| Podman 있음(임시 파일 삭제 후) | 73 passed, 0 skipped, 56초 |

## 4. Windows 환경 문제

| 문제 | 원문 / 증상 | 대응 |
|---|---|---|
| Git Bash 경로 자동 변환 | `podman exec ... ls /app` → `ls: cannot access 'C:/Program Files/Git/app': No such file or directory` | `export MSYS_NO_PATHCONV=1`. 스크립트의 `-v mysql-data:/var/lib/mysql`, JDBC URL 인자는 변환되지 않음을 `podman inspect`로 확인 |
| Git Bash에서 `podman machine ssh` | 현재 폴더에 `NUL`이라는 파일(99바이트, machine의 SSH 호스트 공개키 한 줄)이 생김 | 파일 삭제. `podman machine ssh`는 PowerShell에서 실행한다 |
| `wsl --install --no-distribution` | WSL이 없으면 기본 `wsl.exe`가 안내 문구만 출력 | `winget install Microsoft.WSL`을 관리자 권한으로 실행 |
| 설치 직후 PATH | 현재 셸에서 `podman`을 못 찾음 | 새 터미널을 열거나 PATH를 다시 읽음 |

## 5. 작업 중 실수 기록

- Day32 재현 첫 시도(Podman 설치 전): 셸 현재 디렉터리가 `build/test-results/test` 안이어서 `rm -rf build/test-results`가 `Device or resource busy`로 실패했다. `&&`로 이어 놓아 Gradle이 실행되지 않았고, 다시 실행했다.
- 기준선 로그를 처음에 저장소 밖(`Desktop\baseline-test.log`)에 썼다가 임시 폴더로 옮겼다.
- Git Bash에서 `podman machine ssh`를 실행해 `NUL` 파일을 만들었다(위 4절).
- Podman 설치 전 문서에 Windows용 `DOCKER_HOST=npipe:////./pipe/podman-machine-default`와 `RYUK_DISABLED`를 "미검증 예시"로 적었다. 실제로는 둘 다 필요 없었다.

## 6. 블로그 초안과의 차이

| 초안 위치 | 초안 내용 | 실제 결과 | 수정 제안 |
|---|---|---|---|
| 1장 4) `podman ps` 예시 | infra 이름 `myapp-infra`, IMAGE `localhost/podman-pause:6.1.0` | infra 이름은 `<Pod ID 12자리>-infra`, IMAGE 칸 비어 있음(5.8.8) | 실제 출력으로 교체 |
| 4장 1) Windows | 링크 한 줄 | `winget install Microsoft.WSL`은 관리자 권한 필요, compose provider 없음, Git Bash 경로 변환 | Windows 절에 위 4절 표를 요약해 추가 |
| 4장 2) short name | "프롬프트가 뜨거나 CI에서 실패할 수 있다" | podman machine에서는 `999-podman-machine.conf` 덕분에 프롬프트 없이 docker.io로 해석됨 | "machine에서는 docker.io 하나로 설정돼 있어 괜찮지만, 배포판 설정에 따라 다르므로 정식 이름을 쓴다"로 수정 |
| 4장 2) 빌드 | Dockerfile은 "그대로 동작" | Windows 클론에서는 CRLF `gradlew` 때문에 `/bin/sh: 1: ./gradlew: not found`, exit 127 | `.gitattributes`(`gradlew text eol=lf`) 단락 추가 |
| 4장 3) podman compose | "설치된 docker-compose(또는 podman-compose)를 호출" | Windows 설치본에는 둘 다 없어 `looking up compose provider failed` | provider를 따로 설치해야 한다는 문장과 오류 원문 추가 |
| 4장 4) `podman wait` | `podman wait --condition=healthy` | 5.8.8에서 정상 동작, `-1` 출력 | 그대로 두되, 타이머가 없는 환경 대비로 `timeout`을 감싸는 방법을 선택 사항으로 언급 |
| 4장 4) healthcheck | `--health-timeout` 없음 | compose.yaml에는 `timeout: 5s`가 있음 | "그대로 옮김"이라고 쓰려면 `--health-timeout 5s` 추가 |
| 4장 5) generate | `-f deploy/studyroom-pod.yaml`로 저장소 파일에 바로 씀 | 생성본에는 컨테이너 이름에 Pod 이름이 붙어 있고, hostPort가 mysql에 붙고, PVC 문서·restartPolicy가 없음 | 별도 파일로 뽑은 뒤 위 3.4 비교표처럼 정리한다고 서술 |
| 4장 5) 생성본 설명 | "자동 생성된 긴 볼륨 이름" | 볼륨 이름은 `mysql-data-pvc`, `claimName: mysql-data`로 길지 않았음 | 실제 생성본 기준으로 문장 수정 |
| 4장 5) `podman ps --pod` 주석 | `studyroom-mysql, studyroom-app, studyroom-infra` | infra는 `d76b248239d6-infra` | 주석 수정 |
| 4장 5) 기동 순서 | `restartPolicy: Always`로 다시 뜬다 | 새 볼륨이면 3번 재시작 후 20초 만에 healthy, 기존 볼륨이면 0회·9초(측정) | 측정값 추가 |
| 4장 5) kube down | 정리한다 | 볼륨은 남는다(`Volumes removed:` 비어 있음) | "데이터까지 지우려면 `podman volume rm`" 추가 |
| 4장 5) non-root Dockerfile | `useradd --system --uid 1001 spring`, `USER 1001` | `uid=1001 gid=999` | `groupadd --gid 1001` + `useradd --gid 1001`, `USER 1001:1001` |
| 4장 6) Quadlet | `After=network-online.target`만 | Quadlet이 `podman-user-wait-network-online.service` 의존성을 자동으로 붙임(dry-run 확인) | 한 줄 보충, dry-run 명령 소개 |
| 4장 6) Quadlet 실행 | start → status | `start`는 0.9초 만에 끝나지만 Spring 기동은 그 뒤에 진행됨. `<id>-service` 컨테이너가 추가로 생김. stop해도 볼륨은 남음 | "start가 끝났다고 앱이 준비된 것은 아니다"와 service 컨테이너 설명 추가 |
| 4장 6) linger | `loginctl enable-linger` 권장 | podman machine은 이미 `Linger=yes`. 유닛을 남겨 두면 machine을 켤 때마다 자동으로 떠서 8080을 차지함 | 실습이 끝나면 유닛 파일을 지운다는 정리 단계 추가 |
| 4장 8) 테스트 코드 | `new MySQLContainer<>("docker.io/library/mysql:8")` | 생성자에서 `IllegalStateException` | `asCompatibleSubstituteFor("mysql")` |
| 4장 8) 소켓 설정 | Linux·macOS만 있음, "rootless에서는 Ryuk가 동작하지 않는다" | Windows podman machine(rootless)에서는 설정 없이 연결됐고 Ryuk도 동작함 | Windows 절 추가, Ryuk 문장을 "Linux rootless 소켓 환경에서"로 범위 한정(Linux는 미검증) |
| 4장 8) `@ServiceConnection` | H2 환경변수보다 우선한다 | 확인됨(`dataSourceIsMySqlNotH2` 통과) | 검증 방법(DB 제품명 단언)을 같이 소개 |
| 4장 8) Day32 | "로컬에서 바로 잡혔을 것이다" | 실제로 1064 재현, H2 테스트는 통과 | 가정법을 실측 결과로 교체 |
| 전반 | `mysql:8.0`(1장 예시)과 `mysql:8`(4장) 혼용 | `mysql:8`은 현재 MySQL 8.4 | 태그 표기를 통일하고 8.4가 받아진다는 점 명시 |

## 7. 남은 기술부채

1. Linux rootless 소켓 환경의 Testcontainers 설정(`podman.socket`, Ryuk) 확인
2. `.gitattributes`와 gid 수정을 원본 저장소에도 반영할지 결정
3. kube YAML의 평문 비밀값을 `kind: Secret`으로 분리
4. Quadlet을 실제 리눅스 서버(VM 한 대)에 올려 재부팅 후 자동 시작까지 확인
5. machine 메모리 2GiB에서 MySQL과 앱을 동시에 띄우는 데는 문제가 없었지만, 메모리 사용량은 측정하지 않았음
