# A Dockerfile rather than the platform's default builder on purpose: that
# builder does not read the Gradle toolchain and pins its own JDK (21 at the time
# of writing), so a JDK 25 project silently builds against the wrong one.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# Wrapper and build scripts first, so dependency resolution is cached separately
# from source changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Never run as root.
RUN useradd --system --create-home --uid 10001 app
USER app

COPY --from=build --chown=app:app /src/build/libs/*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

# The platform assigns the port; bind to it rather than the default.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
