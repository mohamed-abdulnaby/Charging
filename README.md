# Prepaid Voice Call Charging System

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-blue.svg" alt="Java 21">
  <img src="https://img.shields.io/badge/PostgreSQL-16-green.svg" alt="PostgreSQL 16">
  <img src="https://img.shields.io/badge/Tomcat-10-orange.svg" alt="Tomcat 10">
  <img src="https://img.shields.io/badge/Docker-Ready-blue.svg" alt="Docker">
  <img src="https://img.shields.io/badge/Database-NeonDB-purple.svg" alt="NeonDB">
</p>

A real-time prepaid voice call charging system that emulates telecom billing. Captures audio via microphone, streams over RTP, deducts balance per minute, generates CDRs, and provides a web dashboard for subscriber management.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Features](#features)
4. [Prerequisites](#prerequisites)
5. [Quick Start](#quick-start)
6. [Database Setup](#database-setup)
7. [Configuration](#configuration)
8. [Building \& Running](#building--running)
9. [API Reference](#api-reference)
10. [Mobile Client Usage](#mobile-client-usage)
11. [Web Dashboard](#web-dashboard)
12. [Asterisk Integration](#asterisk-integration)
13. [CDR \& Logging](#cdr--logging)
14. [Troubleshooting](#troubleshooting)
15. [Project Structure](#project-structure)

---

## Overview

This system implements a **real-time prepaid charging engine** for voice calls, simulating how telecom operators bill subscribers. It consists of three main components:

| Component | Description | Protocol |
|-----------|-------------|----------|
| **Mobile Client** | Captures microphone audio, streams via RTP | Java Standalone |
| **MSC Server** | Receives voice, plays audio, deducts balance, generates CDRs | TCP + UDP/RTP |
| **Web Application** | REST API + Dashboard for subscriber management | Jakarta Servlet |

### Pricing Model

- **Rate**: 1.0 L.E. per minute
- **Charging Interval**: Every 60 seconds
- **Minimum Balance**: 1.0 L.E. to start a call

---

## Architecture

```
+---------------------------------------------------------------------------------------------+
|                           PREPAID CHARGING SYSTEM                                           |
+---------------------------------------------------------------------------------------------+
|                                                                                             |
|  +-------------+                                      +--------------------------------+   |
|  |   Mobile    |                                      |          Tomcat 10             |   |
|  |   Client    |                                      |  +-------------------------+  |   |
|  | (Standalone)|                                      |  |  AppLifecycleListener  |  |   |
|  +------+------+                                      |  |   (auto-starts MSC)     |  |   |
|         |                                             |  +-----------+-------------+  |   |
|         | TCP:88                                      |              |                 |   |
|         |                                             |  +-----------v-------------+  |   |
|         |  +------------------------------------------+--->         MSC              |  |   |
|         |  |                                          |  |  - TCP Signaling :88    |  |   |
|         |  |                                          |  |  - RTP Listener         |  |   |
|         |  |                                          |  |  - Charging Engine      |  |   |
|         |  |                                          |  |  - CDR Generator        |  |   |
|         |  |                                          |  +-----------+-------------+  |   |
|         |  |                                          |              |                 |   |
|         |  |                                          |  +-----------v-------------+  |   |
|         |  |                                          |  |     REST API Layer       |  |   |
|         |  |                                          |  |  /api/users (CRUD)       |  |   |
|         |  |                                          |  |  /api/calls/*            |  |   |
|         |  |                                          |  |  /api/agi/authorize      |  |   |
|         |  |                                          |  +-------------------------+  |   |
|         |  |                                          +-------------------------------+   |
|         |  |                                                              |             |
|  UDP    |  |                                                              |             |
|  RTP    |  |                                                              v             |
|  Audio  |  |                                                     +------------------+      |
|         |  |                                                     |     NeonDB       |      |
|         |  |                                                     |   PostgreSQL     |      |
|         |  |                                                     |    (Cloud)       |      |
|         |  |                                                     |                  |      |
|         |  |                                                     |  +------------+  |      |
|         |  |                                                     |  | Users Table|  |      |
|         |  |                                                     |  +------------+  |      |
|         |  |                                                     |  +------------+  |      |
|         |  |                                                     |  | CDRs Table |  |      |
|         |  |                                                     |  +------------+  |      |
|         |  |                                                     +------------------+      |
|         |  |                                                                               |
|         |  +------------------------------------------------------------------------------+
|         |                      Asterisk PBX (SIP Proxy)                                      |
|         |  Extensions: 6001 (User 1), 6002 (User 2)                                        |
|         |  - Queries /api/agi/authorize for balance check                                 |
|         |  - Enforces call duration limit (L parameter)                                   |
|         |  Ports: SIP/UDP 50:60, RTP 10000-10010                                           |
+---------------------------------------------------------------------------------------------+
```

### Call Flow

```
Mobile Client                      MSC Server                      Database
     |                                  |                                |
     |---- TCP: "Start Call <MSISDN>" -->|                                |
     |<---- TCP: "PORT <UDP_PORT>" ------|                                |
     |                                  |                                |
     |---- UDP/ RTP Voice Stream ------->|  (saves to .wav file)          |
     |                                  |                                |
     |                                  |-- SELECT Balance ------------->|
     |                                  |<--- 100.00 --------------------|
     |                                  |                                |
     |                                  |        [Every 60 seconds]      |
     |                                  |-- UPDATE Balance - 1.0 ------>|
     |                                  |<--- OK -----------------------|
     |                                  |                                |
     |    (when balance depleted)       |                                |
     |<---- TCP: Connection Closed -----|                                |
     |                                  |                                |
     |                                  |-- INSERT CDR ---------------->|
     |                                  |                                |
     |  [Java process exits]            |                                |
```

---

## Features

### Core Features
- [x] **Real-time Audio Capture & Streaming** -- Captures microphone via Java Sound API, streams over RTP
- [x] **Dynamic Balance Deduction** -- Deducts 1 L.E. per minute during active calls
- [x] **Automatic Call Teardown** -- Disconnects calls when balance reaches 0
- [x] **CDR Generation** -- Records call details to database and rotating log files
- [x] **TCP Signaling** -- Reliable session establishment with Start/End Call messages
- [x] **Concurrent Call Support** -- Multiple simultaneous calls handled via thread-per-session
- [x] **RTP Protocol** -- Full RTP header implementation (sequence, timestamp, SSRC)

### Bonus Features
- [x] **Voice Recording to WAV** -- Saves all calls to `/tmp/voice_call_msisdn_{MSISDN}_date_{DATE}_Time_{TIME}.wav`
- [x] **Hourly CDR Log Rotation** -- Using Log4j2 RollingFileAppender
- [x] **Web Call Simulation** -- Simulate calls via REST API for testing
- [x] **Packet Loss Detection** -- Inserts silence for missing RTP packets

### Web Dashboard
- [x] **User Management** -- Full CRUD on Users table (MSISDN, Balance)
- [x] **Active Calls Monitor** -- Real-time view of ongoing calls
- [x] **CDR History** -- View past call records with cost and duration
- [x] **Revenue Dashboard** -- Total revenue aggregation
- [x] **Glassmorphic UI** -- Modern, responsive design

### Asterisk Integration
- [x] **FastAGI Authorization** -- Queries balance before allowing outbound calls
- [x] **Dynamic Duration Limits** -- Sets max call time based on available balance
- [x] **SIP Endpoints** -- Extensions 6001 and 6002 mapped to test subscribers

---

## Prerequisites

| Dependency | Version | Purpose |
|------------|---------|---------|
| Java | 21+ | Runtime (Temurin/Eclipse) |
| Maven | 3.9+ | Build tool |
| Docker | 24+ | Containerization |
| Docker Compose | 2.20+ | Orchestration |
| PostgreSQL Client | 16 | Database CLI tools |
| Audio Input | -- | Microphone for mobile client |

### System Requirements
- **CPU**: 2+ cores
- **RAM**: 2GB minimum
- **Ports Available**:
  - `80` or `8080` (Tomcat HTTP)
  - `88` (TCP Signaling)
  - `4573` (Diameter)
  - `5060` (Asterisk SIP)
  - `10000-10010` (RTP Audio)

---

## Quick Start

### Option 1: Docker (Recommended)

```bash
# 1. Clone and navigate to project
cd /home/zkhattab/Charging

# 2. Build and start all services
docker compose up --build -d

# 3. Verify services are running
docker compose ps

# 4. Check application logs
docker compose logs -f charging-app
```

### Option 2: Local Development

```bash
# 1. Ensure PostgreSQL is running locally
docker compose up -d db

# 2. Build the WAR file
mvn clean package -DskipTests

# 3. Run with Jetty Maven plugin (development)
mvn jetty:run

# Or deploy to Tomcat
cp target/charging-system-1.0-SNAPSHOT. war $CATALINA_HOME/webapps/ROOT. war
```

### Option 3: Standalone MSC + Mobile

```bash
# 1. Compile
javac -d target/classes src/main/java/*.java

# 2. Start MSC (inside Docker or with DB connection)
java -cp target/classes:target/dependency/* MSC

# 3. Start Mobile client (separate terminal)
java -cp target/classes:target/dependency/* Mobile 201001234567
```

---

## Database Setup

### Option A: NeonDB (Cloud - Production)

The system is pre-configured to use **NeonDB** PostgreSQL as the primary database.

**Connection Details:**
```
# Run init script
\i init.sql

# Verify tables
\dt
```

**Sample Data:**
| ID | MSISDN | Balance |
|----|--------|---------|
| 1 | 201001234567 | 100.00 |
| 2 | 201009876543 | 50.00 |
| 3 | 201005551234 | 200.00 |

### Option B: Local Docker PostgreSQL

```bash
# Start local database
docker compose up -d db

# Connect
psql -h localhost -p 5432 -U postgres -d charging

# Run init
\i init.sql
```

### Schema

```sql
-- Users table
CREATE TABLE Users (
    id      SERIAL PRIMARY KEY,
    msisdn  VARCHAR(20) UNIQUE NOT NULL,
    balance NUMERIC(10, 2) NOT NULL DEFAULT 100.0
);

-- Call Detail Records
CREATE TABLE CDRs (
    id             SERIAL PRIMARY KEY,
    msisdn         VARCHAR(20) NOT NULL,
    start_time     TIMESTAMP NOT NULL,
    end_time       TIMESTAMP NOT NULL,
    duration_mins  INT NOT NULL,
    cost           NUMERIC(10, 2) NOT NULL,
    result         VARCHAR(100) NOT NULL,
    final_balance  NUMERIC(10, 2) NOT NULL
);
```

---

## Configuration

### Database Connection (`DatabaseConnection.java`)

```java
// Database connection configured via environment variables
// See docker-compose.yaml or set DB_URL, DB_USER, DB_PASSWORD
//
// Example docker-compose. yaml settings:
//   DB_URL: jdbc:postgresql://your-host/your-db
//   DB_USER: your-username
//   DB_PASSWORD: your-password
```

### NeonDB Connection Example

```bash
# Get connection string from NeonDB Dashboard:
# Project → Connection Details → Connection String
# Then set environment variables:

export DB_URL="postgresql://user:password@host/db?sslmode=require"
export DB_USER="your-username"
export DB_PASSWORD="your-password"
```

### Environment Variable Overrides

```bash
# Override connection string completely
export DB_URL="jdbc:postgresql:// custom-host/db"
export DB_USER="myuser"
export DB_PASSWORD="mypassword"

# Override via docker- compose
DB_URL=jdbc:postgresql://host/db
DB_USER=postgres
DB_PASSWORD=secret
```

### MSC Server Settings

```java
// TCP Signaling Port
private static final int TCP_PORT = 88;

// RTP Audio Port Range (dynamic allocation)
private static int nextUdpPort = 10000;

// Charging Rate (L.E. per minute)
private static final double CHARGE_PER_MINUTE = 1.0;
```

### Asterisk Configuration

```bash
# AGI Authorization Token (must match)
AGI_TOKEN=<SET_IN_ENV>

# SIP Extensions
Extension 6001 --> User 201001234567 (Password: password6001)
Extension 6002 --> User 201009876543 (Password: password6002)
```

### Log4j2 Configuration

```xml
<!-- src/main/resources/log4j2.xml -->
<RollingFile name="CDRFile" fileName="/tmp/calls.cdr" 
             filePattern="/tmp/ calls_ CDR_%d{yyyy_MM_dd_HH}. cdr">
    <PatternLayout pattern="%m%n"/>
    <TimeBasedTriggeringPolicy interval="1"/>
</RollingFile>
```

---

## Building & Running

### Build Commands

```bash
# Clean build
mvn clean package

# With dependencies
mvn clean package -DskipTests

# Build Docker image
docker build -t charging-app:latest .

# Full stack start
docker compose up --build -d
```

### Runtime Options

```bash
# Run standalone MSC (requires DB connection)
java -cp "target/classes:target/dependency/*" MSC

# Run mobile client
java -cp "target/classes:target/dependency/*" Mobile <MSISDN>

# Example
java -cp "target/classes:target/dependency/*" Mobile 201001234567

# With Maven
mvn jetty:run -pl :charging-system
```

### Docker Commands

```bash
# View logs
docker compose logs -f

# Scale (for load testing)
docker compose up -d --scale charging-app=3

# Stop
docker compose down

# Rebuild
docker compose up --build --force-recreate
```

---

## API Reference

All endpoints are prefixed with `/api` and return JSON. CORS is enabled for all origins.

### Users API

#### `GET /api/users`
List all subscribers.

```bash
curl http://localhost:8080/api/users
```

**Response:**
```json
[
  {"id":1,"msisdn":"201001234567","balance":100.0},
  {"id":2,"msisdn":"201009876543","balance":50.0}
]
```

---

#### `POST /api/users`
Create new subscriber.

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"msisdn":"201001111111","balance":100.0}'
```

**Response:**
```json
{"id":4,"msisdn":"201001111111","balance":100.0}
```

---

#### `PUT /api/users`
Update subscriber balance.

```bash
curl -X PUT http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"id":1,"balance":150.0}'
```

**Response:**
```json
{"success":true,"id":1,"balance":150.0}
```

---

#### `DELETE /api/users? id=1`
Delete subscriber.

```bash
curl -X DELETE "http://localhost:8080/api/users? id=1"
```

**Response:**
```json
{"success":true}
```

---

### Calls API

#### `GET /api/ calls/active`
View all active calls.

```bash
curl http://localhost:8080/api/calls/active
```

**Response:**
```json
[
  {
    "msisdn":"201001234567",
    "startTime":"2026-07-12T15:30:00",
    "elapsedMinutes":5,
    "udpPort":10002,
    "callType":"SIP",
    "currentBalance":95.0
  }
]
```

---

#### `GET /api/ calls/cdr`
View call history (last 15 records).

```bash
curl http://localhost:8080/api/calls/cdr
```

**Response:**
```json
[
  {
    "msisdn":"201001234567",
    "startTime":"2026-07-12T15:00:00",
    "endTime":"2026-07-12T15:05:00",
    "duration":5,
    "cost":5.0,
    "result":"Normal call Clearing",
    "finalBalance":95.0
  }
]
```

---

#### `GET /api/ calls/revenue`
Total revenue from all CDRs.

```bash
curl http://localhost:8080/api/calls/revenue
```

**Response:**
```json
{"totalRevenue":150.0}
```

---

#### `POST /api/ calls/simulate`
Simulate a call (for testing without mobile client).

```bash
curl -X POST http://localhost:8080/api/calls/simulate \
  -H "Content-Type: application/json" \
  -d '{"msisdn":"201001234567"}'
```

**Response:**
```json
{"success":true}
```

*Note: Simulated calls deduct 1 L.E. every 5 seconds (accelerated).*

---

#### `POST /api/ calls/hangup`
End a simulated call.

```bash
curl -X POST http://localhost:8080/api/calls/hangup \
  -H "Content-Type: application/json" \
  -d '{"msisdn":"201001234567"}'
```

---

### Asterisk AGI API

#### `GET /api/agi/authorize`
Check caller authorization (used by Asterisk dialplan).

```bash
curl "http://localhost:8080/api/agi/authorize? callerid=201001234567& token=<YOUR_TOKEN>"
```

**Response (Authorized):**
```json
{"authorized":true,"maxDuration":6000000,"balance":100.0}
```

**Response (Insufficient Balance):**
```json
{"authorized":false,"maxDuration":0,"balance":0.0}
```

**Response (Invalid Token):**
```
HTTP 403 Forbidden
{"authorized":false,"maxDuration":0,"balance":0}
```

---

## Mobile Client Usage

### Starting a Call

```bash
# Compile (if not using pre-built)
javac -cp "target/classes:target/dependency/*" \
  -d target/classes \
  src/main/java/Mobile.java

# Start mobile client
java -cp "target/classes:target/dependency/*" Mobile <MSISDN>

# Examples
java -cp "target/classes:target/dependency/*" Mobile 201001234567
java -cp "target/classes:target/dependency/*" Mobile 201009876543
```

### Expected Output

```
Starting voice call as MSISDN 201001234567
Assigned RTP UDP Port: 10002
Capturing Voice from Microphone and send via UDP...
1 minutes elapsed
2 minutes elapsed
3 minutes elapsed
...
```

### Ending a Call

The call ends automatically when:
1. Balance reaches 0
2. User presses `Ctrl+C` (shutdown hook sends "End Call")
3. Server disconnects (balance depleted)

### Audio Format

| Property | Value |
|----------|-------|
| Sample Rate | 16,000 Hz |
| Bit Depth | 16-bit |
| Channels | 1 (Mono) |
| Encoding | Signed, Big-Endian |
| Protocol | RTP |

---

## Web Dashboard

### Access

```
http://localhost:8080/
```

### Dashboard Features

1. **Subscriber Cards** -- Display all users with balance
2. **Quick Recharge** -- Add balance to any subscriber
3. **Active Calls Monitor** -- Real-time call status
4. **CDR History Table** -- Paginated call records
5. **Revenue Stats** -- Total earnings widget

### Adding Balance

1. Click on a subscriber card
2. Enter new balance value
3. Click "Update"

---

## Asterisk Integration

### Dialplan Flow

```
Caller dials 6001 or 6002
        |
        v
Asterisk queries /api/agi/authorize? callerid=<CALLERID>& token=<AGI_TOKEN>
        |
        v
Check balance in Users table
        |
        v
+-- Authorized (balance > 0) --------+-- Rejected --+
|                                      |              |
| Calculate max duration               | Play         |
| (balance / 1.0 * 60000ms)           | "vm-goodbye" |
|                                      |              |
| Dial with time limit                 | Hangup       |
| Dial(PJSIP/${EXTEN},L(max))         |              |
+-------------------------------------+--------------+
```

### SIP Phones Configuration

| Property | Phone 1 (6001) | Phone 2 (6002) |
|----------|----------------|----------------|
| Extension | 6001 | 6002 |
| CallerID | User 1 <201001234567> | User 2 <201009876543> |
| Username | 6001 | 6002 |
| Password | password6001 | password6002 |
| Codecs | ulaw, alaw | ulaw, alaw |

---

## CDR & Logging

### CDR Format

Each call generates a CDR record with:
```
MSISDN, Start_Time, End_Time, Duration_ Minutes, Result, Cost, Final_ Balance
```

**Example:**
```
201001234567, 2026-07-12T15:00:00, 2026-07-12T15:05:00, 5, Normal call Clearing, 5.0, 95.0
```

### CDR Results

| Result | Meaning |
|--------|---------|
| `Normal call Clearing` | Call ended successfully |
| `user not found on DB` | MSISDN not in Users table |

### Log File Rotation

CDR logs are rotated hourly via Log4j2:

```
/tmp/calls.cdr                          <-- Current hour
/tmp/calls_ CDR_2026_07_12_10.cdr       <-- Previous hours
/tmp/calls_ CDR_2026_07_12_11.cdr
...
```

### Voice Recording

All calls are recorded to WAV files:

```
/tmp/voice_ call_ msisdn_201001234567_ date_2026_07_12_ Time_15_00_00. wav
```

**WAV Format:**
- Sample Rate: 16,000 Hz
- Bit Depth: 16-bit
- Channels: 1 (Mono)
- Format: PCM Signed

---

## Troubleshooting

### Connection Issues

**Problem:** `Connection refused` to MSC
```
* Ensure MSC is running (check logs)
* Verify port 88 is not blocked
* Check firewall rules
```

**Problem:** `PostgreSQL connection failed`
```
* Verify database credentials
* Check NeonDB connection string
* Ensure SSL settings are correct
* Test with: psql "connection-string"
```

### Audio Issues

**Problem:** No audio on MSC
```
* Check microphone permissions
* Verify UDP port range (10000-10010) is open
* Test with: arecord | aplay (local)
```

**Problem:** Poor audio quality
```
* Check network latency
* Verify sample rate matches (16kHz)
* Check for packet loss in logs
```

### Asterisk Issues

**Problem:** Asterisk not starting
```
* Check volume mounts
* Verify sip.conf syntax: asterisk -vvvvvvc
* Check logs: docker compose logs asterisk
```

**Problem:** AGI authorization fails
```
* Verify AGI_TOKEN matches in .env and asterisk config
* Test endpoint: curl "http://localhost:8080/api/agi/authorize? callerid=201001234567& token=<YOUR_TOKEN>"
```

### Database Issues

**Problem:** Users not persisting
```
* Check NeonDB connection
* Verify schema was initialized
* Check disk space on NeonDB tier
```

---

## Project Structure

```
Charging/
|-- src/
|   +-- main/
|       +-- java/
|       |   +-- AppLifecycleListener.java    # Auto- starts MSC daemon
|       |   +-- AsteriskAgiServlet.java       # FastAGI authorization endpoint
|       |   +-- CallServlet.java              # REST API for calls & simulation
|       |   +-- CallSessionInfo.java          # Active call tracking POJO
|       |   +-- DatabaseConnection.java        # PostgreSQL connection pool
|       |   +-- MSC.java                      # Main MSC server (TCP + RTP)
|       |   +-- Mobile.java                  # Mobile client (mic capture + RTP)
|       |   +-- UserServlet.java             # User CRUD REST API
|       +-- resources/
|       |   +-- log4j2.xml                   # Log4j2 configuration
|       +-- webapp/
|           +-- index.html                   # Dashboard UI
|           +-- css/
|           |   +-- style.css                # Glassmorphic styles
|           +-- WEB-INF/
|               +-- web.xml                  # Servlet configuration
|-- asterisk_config/
|   +-- extensions.conf                      # Dialplan with AGI call
|   +-- sip.conf / pjsip.conf              # SIP endpoints
|-- compose.yaml                             # Docker Compose orchestration
|-- Dockerfile                               # Multi- stage Docker build
|-- init.sql                                 # Database initialization
|-- pom.xml                                  # Maven configuration
|-- README.md                                # This file
|-- walkthrough.md                           # Development walkthrough
+-- team_presentation.md                     # Team documentation
```

---

## License

This project is for **educational purposes** as part of the 2026 Telecom Systems course.

**Authors:** CS Students   
**Institution:** Faculty of Computer Science   
**Year:** 2026

---

## Acknowledgments

- Java Sound API for audio capture
- Log4j2 for structured logging
- NeonDB for cloud PostgreSQL
- Asterisk PBX for SIP integration
- Jakarta EE for enterprise Java

---

<p align="center">
  <strong>Built with Java and audio for real-time telecom charging</strong>
</p>