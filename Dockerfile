FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/repair-system-1.0.0.jar app.jar
RUN mkdir -p /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
