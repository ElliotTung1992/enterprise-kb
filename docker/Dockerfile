# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Use custom Maven settings (Aliyun mirror + Spring repos)
COPY docker/maven-settings.xml /root/.m2/settings.xml

# Copy parent POM and all module POMs first (better layer caching)
COPY pom.xml .
COPY kb-common/pom.xml    kb-common/pom.xml
COPY kb-auth/pom.xml      kb-auth/pom.xml
COPY kb-user/pom.xml      kb-user/pom.xml
COPY kb-document/pom.xml  kb-document/pom.xml
COPY kb-search/pom.xml    kb-search/pom.xml
COPY kb-knowledge-graph/pom.xml kb-knowledge-graph/pom.xml
COPY kb-app/pom.xml       kb-app/pom.xml

# Pre-download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B -q

# Copy source
COPY kb-common/src      kb-common/src
COPY kb-auth/src        kb-auth/src
COPY kb-user/src        kb-user/src
COPY kb-document/src    kb-document/src
COPY kb-search/src      kb-search/src
COPY kb-knowledge-graph/src kb-knowledge-graph/src
COPY kb-app/src         kb-app/src

# Build fat JAR
RUN mvn -pl kb-app -am package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/kb-app/target/*.jar app.jar

RUN mkdir -p /app/uploads

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
