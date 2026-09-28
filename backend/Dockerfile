# Spring Boot 서비스 공용 이미지. (CLAUDE.md §47 Phase 7, §54)
#
# 빌드 컨텍스트는 backend/ 이고, 서비스는 빌드 인자로 고른다.
#
#   docker build -f backend/Dockerfile --build-arg SERVICE=gateway-service -t wts/gateway backend
#
# jar는 Gradle이 미리 만들어 둔 것을 쓴다. 이미지 안에서 Gradle을 돌리지 않는다.
# CI가 이미 'Backend Test' 단계에서 빌드했고, 두 번 빌드할 이유가 없다.

FROM eclipse-temurin:21-jre-alpine

ARG SERVICE
# 컨테이너 밖에서도 어떤 서비스인지 보이게 남긴다.
LABEL org.opencontainers.image.title="wts-${SERVICE}"

# root로 돌리지 않는다.
RUN addgroup -S wts && adduser -S wts -G wts
WORKDIR /app

# -SNAPSHOT.jar만 가져온다. plain.jar(의존성 없는 껍데기)는 제외한다.
COPY --chown=wts:wts ${SERVICE}/build/libs/*-SNAPSHOT.jar app.jar

USER wts

# 컨테이너 메모리 한도를 JVM이 인식하게 한다 (§54 – Container Ready).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
# 시각은 UTC로 기록한다 (§43).
ENV TZ=UTC

# 정지 신호를 JVM이 직접 받아야 Graceful Shutdown이 동작한다 (§54).
# exec 형식이라 java가 PID 1이 된다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
