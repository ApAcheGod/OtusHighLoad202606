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

COPY utils/src utils/src
COPY grpc-api/src grpc-api/src
COPY app/src app/src
COPY chat-service/src chat-service/src
COPY auth-service/src auth-service/src
COPY user-service/src user-service/src
COPY post-service/src post-service/src
COPY friend-service/src friend-service/src
COPY feed-service/src feed-service/src
COPY gateway/src gateway/src
RUN --mount=type=cache,id=otus-gradle,target=/home/gradle/.gradle,sharing=locked \
    gradle --no-daemon \
      :app:bootJar \
      :auth-service:bootJar \
      :user-service:bootJar \
      :post-service:bootJar \
      :friend-service:bootJar \
      :feed-service:bootJar \
      :chat-service:bootJar \
      :gateway:bootJar

FROM eclipse-temurin:25-jre AS chat-service
WORKDIR /app
COPY --from=build /app/chat-service/build/libs/chat-service.jar chat-service.jar
EXPOSE 8083
CMD ["java", "-jar", "chat-service.jar"]

FROM eclipse-temurin:25-jre AS auth-service
WORKDIR /app
COPY --from=build /app/auth-service/build/libs/auth-service.jar auth-service.jar
EXPOSE 8084
EXPOSE 9090
CMD ["java", "-jar", "auth-service.jar"]

FROM eclipse-temurin:25-jre AS user-service
WORKDIR /app
COPY --from=build /app/user-service/build/libs/user-service.jar user-service.jar
EXPOSE 8085
EXPOSE 9092
CMD ["java", "-jar", "user-service.jar"]

FROM eclipse-temurin:25-jre AS post-service
WORKDIR /app
COPY --from=build /app/post-service/build/libs/post-service.jar post-service.jar
EXPOSE 8086
EXPOSE 9093
CMD ["java", "-jar", "post-service.jar"]

FROM eclipse-temurin:25-jre AS friend-service
WORKDIR /app
COPY --from=build /app/friend-service/build/libs/friend-service.jar friend-service.jar
EXPOSE 8087
EXPOSE 9094
CMD ["java", "-jar", "friend-service.jar"]

FROM eclipse-temurin:25-jre AS feed-service
WORKDIR /app
COPY --from=build /app/feed-service/build/libs/feed-service.jar feed-service.jar
EXPOSE 8089
EXPOSE 9095
CMD ["java", "-jar", "feed-service.jar"]

FROM eclipse-temurin:25-jre AS gateway
WORKDIR /app
COPY --from=build /app/gateway/build/libs/gateway.jar app.jar
EXPOSE 8082
CMD ["java", "-jar", "app.jar"]

FROM eclipse-temurin:25-jre AS app
WORKDIR /app
COPY --from=build /app/app/build/libs/app.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
