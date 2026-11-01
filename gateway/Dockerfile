FROM gradle:jdk25 AS build
WORKDIR /app

COPY buildSrc/src buildSrc/src
COPY buildSrc/build.gradle.kts buildSrc/settings.gradle.kts buildSrc/
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY app/build.gradle.kts app/build.gradle.kts
COPY utils/build.gradle.kts utils/build.gradle.kts
COPY gateway/build.gradle.kts gateway/build.gradle.kts
RUN gradle --no-daemon :gateway:dependencies :utils:dependencies

COPY utils/src utils/src
COPY gateway/src gateway/src
RUN gradle :gateway:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/gateway/build/libs/gateway.jar app.jar
EXPOSE 8082
CMD ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
