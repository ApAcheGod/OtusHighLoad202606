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
RUN gradle --no-daemon :post-service:dependencies

COPY grpc-api/src grpc-api/src
COPY utils/src utils/src
COPY post-service/src post-service/src
RUN gradle :post-service:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/post-service/build/libs/post-service.jar post-service.jar
EXPOSE 8086
EXPOSE 9093
CMD ["java", "-XX:MaxRAMPercentage=60", "-jar", "post-service.jar"]
