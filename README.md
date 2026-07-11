# Prepaid Charging for Voice Call

This project emulates real-time voice calls and a prepaid charging system. It consists of a Mobile application that captures voice from a microphone and streams it over RTP to a Mobile Switching Center (MSC) application. The MSC receives the voice data, handles real-time balance deduction from a PostgreSQL database, saves the audio to a file, and generates Call Detail Records (CDRs) upon call completion.

## Features

- **Voice Emulation & Streaming:** Captures live audio from the microphone and transmits it using the RTP (Real-Time Transport Protocol) over UDP.
- **Real-Time Charging:** Deducts 1 L.E. per minute directly from the user's balance in the database while the call is active.
- **Concurrent Call Handling:** The MSC handles multiple active calls simultaneously using a multi-threaded architecture.
- **Voice Recording:** MSC saves incoming RTP voice streams into standard `.wav` files locally.
- **CDR Generation & Log Rotation:** Generates CDRs for every call, formatted and rotated hourly using Log4j2.

## Prerequisites

- **Java Development Kit (JDK):** Java 8 or higher.
- **PostgreSQL:** Installed and running locally.
- **Microphone:** A working microphone is required for the Mobile application to capture voice.
- **Dependencies (Included):**
  - `postgresql-42.7.11.jar`
  - `log4j-api-2.23.1.jar`
  - `log4j-core-2.23.1.jar`

## Database Setup

1. Open your PostgreSQL client (e.g., `psql` or pgAdmin).
2. Create a new database named `charging`:
   ```sql
   CREATE DATABASE charging;
   ```
3. Connect to the `charging` database and create the `Users` table:
   ```sql
   CREATE TABLE Users (
       ID SERIAL PRIMARY KEY,
       MSISDN VARCHAR(20) UNIQUE NOT NULL,
       Balance DECIMAL NOT NULL
   );
   ```
4. Insert a test user with a balance:
   ```sql
   INSERT INTO Users (MSISDN, Balance) VALUES ('01223456789', 50.0);
   ```
5. **Important:** The `DatabaseConnection.java` file is excluded from version control (via `.gitignore`). You must create this file in the root directory (`DatabaseConnection.java`) with your local PostgreSQL credentials:
   ```java
   import java.sql.Connection;
   import java.sql.DriverManager;

   public class DatabaseConnection {
       private static final String URL = "jdbc:postgresql://localhost:5432/charging";
       private static final String USER = "your_username"; // Change to your postgresql username
       private static final String PASSWORD = "[PASSWORD]";  // Change to your postgresql password

       public static Connection getConnection() throws Exception {
           Class.forName("org.postgresql.Driver");
           return DriverManager.getConnection(URL, USER, PASSWORD);
       }
   }
   ```

## Compilation

Navigate to the project root directory where the `.java` files are located. Compile the Java files by including the necessary `.jar` files in the classpath:

**Linux / macOS:**
```bash
javac -cp .:log4j-api-2.23.1.jar:log4j-core-2.23.1.jar:postgresql-42.7.11.jar *.java
```

## Running the Application

### 1. Start the MSC Application
The MSC application must be started first so it can listen for incoming connections.

**Linux / macOS:**
```bash
java -cp .:log4j-api-2.23.1.jar:log4j-core-2.23.1.jar:postgresql-42.7.11.jar MSC DatabaseConnection
```

*Expected Output:*
```text
Waiting for voice call Signaling start message via TCP port: 8888
```

### 2. Start the Mobile Application
Once the MSC is running, start the Mobile application from a separate terminal. Replace `<Phone number>` with the MSISDN you inserted into the database.

```bash
java Mobile 01223456789
```

*Expected Output:*
```text
Starting voice call as MSISDN 01223456789
Assigned RTP UDP Port: 10000
Capturing Voice from Microphone and send via UDP...
1 minutes elapsed
2 minutes elapsed
```

### 3. Ending the Call
To end the call, simply stop the Mobile application (e.g., using `Ctrl+C`). The shutdown hook will automatically send an "End Call" signal to the MSC.

Upon ending the call, the MSC will log a CDR line and stop the charging process.

## Output Details

- **Audio Recordings:** Saved in the `/tmp/` directory.
  *Example:* `/tmp/voice_call_msisdn_01223456789_date_2026_07_11_Time_15_30_00.wav`
- **Call Detail Records (CDRs):** Saved and rotated hourly in the `/tmp/` directory.
  *Example:* `/tmp/calls_CDR_2026_07_11_15.cdr`
