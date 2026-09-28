# Build with the project's Gradle wrapper (Gradle 9.2.1 is needed for Java 25).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Resolve dependencies in their own layer so code-only changes rebuild quickly.
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /app/app.jar app.jar
USER app
EXPOSE 8080
# Size the heap from the container's memory limit; override with -e JAVA_OPTS=...
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
