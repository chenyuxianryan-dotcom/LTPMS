FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY backend/target/*.jar app.jar
COPY backend/src/main/resources/fonts /app/fonts

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
