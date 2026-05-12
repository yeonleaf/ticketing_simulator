# 1단계: 빌드
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Gradle wrapper와 설정 파일 먼저 복사 (캐싱 최적화)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# 의존성만 먼저 다운로드 (캐시 레이어)
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사 후 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행
FROM eclipse-temurin:21-jre
WORKDIR /app

# 빌드 단계에서 만든 jar만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djdk.tracePinnedThreads=full", "-jar", "app.jar"]