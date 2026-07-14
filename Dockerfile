# ============================================================
# Stage 1: Build – compile and package the WAR with Maven
# SOTA: Multi-stage build keeps runtime image small & secure.
# Network fix: DNS inside build stage (some container runtimes
# don't inherit host DNS). Also supports offline via host .m2.
# ============================================================
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# ── Network resilience ─────────────────────────────────────
# If the build container can't resolve Maven Central,
# force a public DNS. This is a harmless no-op when DNS
# already works and a life-saver when it doesn't.
RUN apt-get update -qq && apt-get install -y -qq inetutils-ping curl dnsutils \
    && rm -rf /var/lib/apt/lists/* \
    && echo "nameserver 8.8.8.8\nnameserver 8.8.4.4" > /etc/resolv.conf

# ── Dependency layer caching ───────────────────────────────
# We copy pom.xml FIRST so Docker caches this layer.
# If pom.xml hasn't changed, the next RUN is skipped.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# ── Compile & package ──────────────────────────────────────
COPY src ./src
RUN mvn clean package -DskipTests

# ============================================================
# Stage 2: Runtime – lightweight Tomcat 10 (JRE 21)
# ============================================================
FROM docker.io/library/tomcat:10-jre21-temurin

# Remove default apps so ROOT.war is served at /
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the WAR built in stage 1
COPY --from=build /app/target/charging-system-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war

# HTTP / SIP / AGI / RTP ports
EXPOSE 8080/tcp 8888/tcp 4573/tcp
EXPOSE 10000-10010/udp

CMD ["catalina.sh", "run"]
