FROM gradle:jdk25 AS build
WORKDIR /app

COPY buildSrc/src buildSrc/src
COPY buildSrc/build.gradle.kts buildSrc/settings.gradle.kts buildSrc/
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY app/build.gradle.kts app/build.gradle.kts
COPY chat-service/build.gradle.kts chat-service/build.gradle.kts
COPY auth-service/build.gradle.kts auth-service/build.gradle.kts
COPY user-service/build.gradle.kts user-service/build.gradle.kts
COPY post-service/build.gradle.kts post-service/build.gradle.kts
COPY friend-service/build.gradle.kts friend-service/build.gradle.kts
COPY feed-service/build.gradle.kts feed-service/build.gradle.kts
COPY grpc-api/build.gradle.kts grpc-api/build.gradle.kts
COPY utils/build.gradle.kts utils/build.gradle.kts
COPY gateway/build.gradle.kts gateway/build.gradle.kts
RUN gradle --no-daemon :app:dependencies :utils:dependencies

COPY utils/src utils/src
COPY grpc-api/src grpc-api/src
COPY app/src app/src
RUN gradle :app:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/app/build/libs/app.jar app.jar
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
