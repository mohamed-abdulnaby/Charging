# ============================================================
# Stage 1: Build – compile and package the WAR with Maven
# ============================================================
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Cache dependencies first (layer caching optimisation)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime – lightweight Tomcat 10 image
# ============================================================
FROM docker.io/library/tomcat:10-jre21-temurin

# Clean default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the built WAR file as ROOT.war
COPY --from=build /app/target/charging-system-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war

# HTTP / SIP / Diameter / RTP ports
EXPOSE 8080/tcp 8888/tcp 4573/tcp
EXPOSE 10000-10010/udp

CMD ["catalina.sh", "run"]
