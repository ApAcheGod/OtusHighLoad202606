# syntax=docker/dockerfile:1.7

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

COPY grpc-api/src grpc-api/src
COPY utils/src utils/src
COPY user-service/src user-service/src
RUN --mount=type=cache,id=otus-gradle,target=/home/gradle/.gradle,sharing=locked \
    gradle --no-daemon :user-service:bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/user-service/build/libs/user-service.jar user-service.jar
EXPOSE 8085
EXPOSE 9092
CMD ["java", "-jar", "user-service.jar"]
