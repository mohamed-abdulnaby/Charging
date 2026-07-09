import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

public class MSC {
    private static final int TCP_PORT = 8888;
    private static final int UDP_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;
    private static final double CHARGE_PER_MINUTE = 1.0;

    private static volatile boolean callActive = false;
    private static String currentMsisdn = "";
    private static LocalDateTime startTime;
    private static int elapsedMinutes = 0;

    public static void main(String[] args) {
        System.out.println("Waiting for voice call");
        System.out.println("Signaling start message via TCP");

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String signal;
                while ((signal = in.readLine()) != null) {
                    if (signal.startsWith("Start Call ")) {
                        currentMsisdn = signal.replace("Start Call ", "").trim();
                        System.out.println("Accept Voice call start signaling message from MSISDN " + currentMsisdn);

                        startTime = LocalDateTime.now();
                        callActive = true;
                        elapsedMinutes = 0;

                        Thread writeAudioFileThread = new Thread(MSC::WriteAudioFile);
                        writeAudioFileThread.start();

                        Thread chargingThread = new Thread(MSC::handleRealTimeCharging);
                        chargingThread.start();
                    } else if (signal.equals("End Call")) {
                        callActive = false;
                        System.out.println("Call End after receiving end call signaling message");
                        generateCDR();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void WriteAudioFile() {
        String path = currentMsisdn + "+" + startTime + ".wav";
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
            socket.setSoTimeout(1000);
            ByteArrayOutputStream rawAudio = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];

            while (callActive) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    rawAudio.write(packet.getData(), 0, packet.getLength());
                } catch (SocketTimeoutException e) {
                    // Loop back and check callActive
                }
            }

            // Write proper WAV file with header
            byte[] audioData = rawAudio.toByteArray();
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream audioStream = new AudioInputStream(bais, format, audioData.length / format.getFrameSize());
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, new File(path));
            System.out.println("Audio saved to " + path + " (" + audioData.length + " bytes)");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleRealTimeCharging() {
        while (callActive) {
            try {
                Thread.sleep(60000);
                if (!callActive)
                    break;

                elapsedMinutes++;
                deductBalance(currentMsisdn, CHARGE_PER_MINUTE);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static void deductBalance(String msisdn, double amount) {
        String query = "UPDATE Users SET Balance = Balance - ? WHERE MSISDN = ?";
        try (Connection conn = getDatabaseConnection();
                PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, amount);
            ps.setString(2, msisdn);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("DB Charging Error: " + e.getMessage());
        }
    }

    private static double getFinalBalance(String msisdn) {
        String query = "SELECT Balance FROM Users WHERE MSISDN = ?";
        try (Connection conn = getDatabaseConnection();
                PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, msisdn);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getDouble("Balance");
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    private static void generateCDR() {
        LocalDateTime endTime = LocalDateTime.now();
        double cost = elapsedMinutes * CHARGE_PER_MINUTE;
        double finalBalance = getFinalBalance(currentMsisdn);
        String callResult = (finalBalance == 0.0 && cost > 0) ? "user not found on DB" : "Normal call Clearing";

        // Format required pattern: MSISDN, Start, End, Duration, Result, Cost, Balance
        String cdrLine = String.format("%s, %s, %s, %d, %s, %.0f, %.0f",
                currentMsisdn, startTime, endTime, elapsedMinutes, callResult, cost, finalBalance);

        System.out.println("Generating CDR line: " + cdrLine);

        File file = new File("./calls_cdr.csv");
        file.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(file, true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw)) {
            out.println(cdrLine);
        } catch (IOException e) {
            System.err.println("Failed to write CDR to disk: " + e.getMessage());
        }
    }

    private static Connection getDatabaseConnection() throws Exception {
        String url = "jdbc:postgresql://localhost:5432/charging";
        String user = "postgres";
        String password = "123456";
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(url, user, password);
    }
}