FROM gradle:8.10-jdk25 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
RUN gradle --no-daemon dependencies
COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
