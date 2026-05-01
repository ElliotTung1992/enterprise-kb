# Runtime only — JAR is pre-built locally via: mvn clean package -DskipTests -pl kb-app -am
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY kb-app/target/*.jar app.jar

RUN mkdir -p /app/uploads

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
