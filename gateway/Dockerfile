FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Download deps first (layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build fat JAR
COPY src ./src
RUN mvn package -DskipTests -q

# Minimal runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
