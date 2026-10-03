#!/usr/bin/env bash
# compose.yaml(mysql + app)을 Podman Pod 하나로 옮겨 띄운다.
#
#   Compose: app → mysql:3306   (서비스 이름 = 컨테이너 네트워크 안의 호스트명)
#   Pod    : app → localhost:3306 (같은 Pod의 컨테이너는 네트워크 네임스페이스를 공유)
#
# 바깥으로는 8080만 publish한다. 3306은 Pod 안에서만 쓰므로 호스트에 열지 않는다.
# 계정·JWT 값은 compose.yaml에 있는 로컬/CI 전용 기본값을 그대로 쓴다(운영 값 아님).
#
# 사용법: ./scripts/podman-pod-up.sh   (먼저 podman build -t localhost/study-room-api:dev ./app)
set -euo pipefail

POD=studyroom
MYSQL=studyroom-mysql
APP=studyroom-app
IMAGE=localhost/study-room-api:dev

MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-studyroom_root}
MYSQL_USER=${MYSQL_USER:-studyroom}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-studyroom}
JWT_SECRET=${JWT_SECRET:-studyroom-docker-secret-key-must-be-at-least-32-bytes-long}
HEALTH_TIMEOUT_SECONDS=${HEALTH_TIMEOUT_SECONDS:-180}

# 1. Pod 생성 — Infra 컨테이너가 네트워크 네임스페이스와 8080 포트를 소유한다.
podman pod create --name "$POD" -p 8080:8080

# 2. MySQL — compose.yaml과 같은 healthcheck. -h localhost는 유닉스 소켓으로 붙어서
#    초기화용 임시 서버(TCP 꺼짐)에도 응답하므로, 127.0.0.1 + TCP로 "3306이 열렸다"를 확인한다.
podman volume exists mysql-data || podman volume create mysql-data
podman run -d --pod "$POD" --name "$MYSQL" \
  -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  -e MYSQL_DATABASE=studyroom \
  -e MYSQL_USER="$MYSQL_USER" -e MYSQL_PASSWORD="$MYSQL_PASSWORD" \
  -v mysql-data:/var/lib/mysql \
  --health-cmd "mysqladmin ping -h 127.0.0.1 --protocol=tcp -uroot -p$MYSQL_ROOT_PASSWORD" \
  --health-interval 5s --health-timeout 5s --health-retries 20 --health-start-period 30s \
  docker.io/library/mysql:8

# 3. depends_on: condition: service_healthy 대신 — healthy가 될 때까지 기다린다.
#    podman wait 에는 타임아웃 옵션이 없다. healthcheck는 systemd 타이머가 주기적으로 실행하는데,
#    타이머가 없는 환경(systemd 없는 컨테이너·일부 VM)에서는 상태가 영원히 starting에 머물 수 있다.
#    그래서 timeout으로 감싸고, 실패하면 healthcheck를 직접 실행하는 루프로 넘어간다.
echo "waiting for $MYSQL to become healthy (max ${HEALTH_TIMEOUT_SECONDS}s)..."
if timeout "$HEALTH_TIMEOUT_SECONDS" podman wait --condition=healthy "$MYSQL"; then
  echo "$MYSQL is healthy (podman wait --condition=healthy)"
else
  echo "podman wait --condition=healthy failed or timed out — falling back to podman healthcheck run"
  for i in $(seq 1 $((HEALTH_TIMEOUT_SECONDS / 5))); do
    if podman healthcheck run "$MYSQL" >/dev/null 2>&1; then
      echo "$MYSQL is healthy (podman healthcheck run, attempt $i)"
      break
    fi
    sleep 5
  done
  podman healthcheck run "$MYSQL" >/dev/null 2>&1 || {
    echo "$MYSQL never became healthy"
    podman logs "$MYSQL" | tail -50
    exit 1
  }
fi

# 4. 앱 — docker 프로필의 jdbc:mysql://mysql:3306 을 환경변수로 덮어쓴다.
#    OS 환경변수(SPRING_DATASOURCE_URL)가 application-docker.yml보다 우선순위가 높다(Day29).
podman run -d --pod "$POD" --name "$APP" \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/studyroom?allowPublicKeyRetrieval=true&useSSL=false' \
  -e MYSQL_USER="$MYSQL_USER" -e MYSQL_PASSWORD="$MYSQL_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" \
  --restart on-failure:3 \
  "$IMAGE"

# 5. 스프링 컨텍스트가 뜰 때까지 /health 재시도(JRE 이미지에는 curl이 없어 호스트에서 확인).
for i in $(seq 1 30); do
  if curl --fail --silent http://localhost:8080/health; then
    echo
    echo "health check passed"
    podman ps -a --pod
    exit 0
  fi
  echo "waiting for app... ($i/30)"
  sleep 2
done
echo "app never responded on /health"
podman ps -a --pod
podman logs "$APP" | tail -80
exit 1
