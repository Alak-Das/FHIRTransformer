# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy maven executable and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Convert line endings of mvnw to unix just in case
RUN sed -i 's/\r$//' mvnw

# Go-offline to download dependencies (optional but good for caching)
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the jar from the build stage
COPY --from=build /app/target/fhir-transformer-0.0.1-SNAPSHOT.jar app.jar

# Expose the port
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
