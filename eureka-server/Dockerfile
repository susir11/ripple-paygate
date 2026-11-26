# Two stages: build the jar inside Docker, then copy only the jar into a small image.
# This means `docker compose up --build` always runs your latest code — there is
# no need to run Maven on your own machine first.

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Copy the Maven wrapper and pom first. Docker caches each step, so as long as
# pom.xml doesn't change, the dependency download below is reused on rebuilds.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Now the source code, then build. Tests run separately (./mvnw test), not here.
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar /app.jar

# ---- Stage 2: run ----
# The JRE image only has what's needed to RUN Java, so it is much smaller than the JDK.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
