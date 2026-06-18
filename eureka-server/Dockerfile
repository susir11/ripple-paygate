# Base image: a lightweight Linux distro with Java 21 already installed.
# We use this instead of a "full" OS image because it's much smaller and faster to build.
FROM eclipse-temurin:21-jdk

# All commands below run from this folder inside the container
WORKDIR /app

# Copy the jar you built earlier (via mvn clean package) into the container,
# and rename it to a fixed name so the next line doesn't need to guess the version number
COPY target/*.jar app.jar

# This is the command Docker runs when the container starts.
# It's the same as running "java -jar app.jar" on your own machine.
ENTRYPOINT ["java", "-jar", "app.jar"]