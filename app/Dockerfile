# Day31 — 멀티스테이지 빌드. 1단계(빌드)에는 JDK+Gradle이 다 들어있어서 무겁고,
# 2단계(실행)에는 컴파일된 jar 하나만 옮겨서 최종 이미지를 가볍게 만든다.
#
# 공식 gradle 이미지 태그가 이 프로젝트의 gradle-wrapper.properties 버전(8.14.5)과 정확히
# 일치하지 않을 수 있어서, JDK 이미지 위에서 프로젝트 자체 gradlew(wrapper)를 그대로 쓴다 —
# wrapper가 알아서 정확한 gradle 버전을 내려받아 빌드해준다.

# Podman 실험 — FROM 이미지는 레지스트리까지 적은 정식 이름(fully qualified name)으로 쓴다.
# "eclipse-temurin:17-jdk" 같은 short name은 Podman이 registries.conf의 short-name-mode·
# unqualified-search-registries 설정으로 어디서 받을지 정하는데, TTY가 없는 CI에서는 고를 수 없어
# 실패할 수 있다. docker.io/library/... 는 Docker에서도 같은 이미지를 가리키므로 손해가 없다.

# ---- 1단계: 빌드 ----
FROM docker.io/library/eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew
# 이미지 안에서는 테스트를 또 돌릴 필요가 없다 — CI(Day32)가 이미 test 잡에서 검증한다.
RUN ./gradlew bootJar --no-daemon -x test

# ---- 2단계: 실행 ----
FROM docker.io/library/eclipse-temurin:17-jre
# Podman 실험 — root가 아닌 실행 전용 사용자. 쿠버네티스 Pod Security Standard의 restricted 정책은
# root 실행 컨테이너를 거부한다. 이름(spring)이 아니라 숫자 UID로 USER를 지정해야
# runAsNonRoot 검사가 이미지 메타데이터만 보고 "root 아님"을 판정할 수 있다.
RUN useradd --system --uid 1001 spring
WORKDIR /app
COPY --from=build --chown=1001:1001 /workspace/build/libs/*.jar app.jar
USER 1001

# Day29에서 만든 프로필 분리 — 컨테이너로 뜰 때는 항상 docker 프로필(application-docker.yml)을 쓴다.
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
