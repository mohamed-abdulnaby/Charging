import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MSC {
    private static final int TCP_PORT = 8888;
    // AtomicInteger for thread safety — multiple CallSession threads share this
    private static final java.util.concurrent.atomic.AtomicInteger nextUdpPort =
        new java.util.concurrent.atomic.AtomicInteger(10000);
    public static final java.util.concurrent.ConcurrentHashMap<String, CallSessionInfo> activeSessions = 
        new java.util.concurrent.ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Waiting for voice call Signaling start message via TCP port: " + TCP_PORT);

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                int assignedUdpPort = getNextUdpPort();
        		
        		CallSession session = new CallSession(clientSocket, assignedUdpPort);
        		new Thread(session).start();
        	}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static int getNextUdpPort() {
        // stay within the 10000-10010 udp range mapped in compose.yaml
        int port = nextUdpPort.getAndUpdate(p -> p >= 10010 ? 10000 : p + 1);
        return port;
    }
}

class CallSession implements Runnable {
	private static final int BUFFER_SIZE = 1024;
    private static final int RTP_HEADER_SIZE = 12;
    private static final double CHARGE_PER_MINUTE = 1.0;
    private static final Logger cdrLogger = LogManager.getLogger("CDRLogger");
    
    private Socket tcpSocket;
    private int udpPort;
    private volatile boolean callActive = false;
    private String msisdn = "";
    private String callResultCode = null;
    private LocalDateTime startTime;
    private int elapsedMinutes = 0;
    private double currentBalance = 0.0;

	public CallSession(Socket tcpSocket, int udpPort) {
		this.tcpSocket = tcpSocket;
		this.udpPort = udpPort;
	}
	
	@Override
	public void run() {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
			 PrintWriter out = new PrintWriter(tcpSocket.getOutputStream(), true)) {
			
			String signal;
            while ((signal = in.readLine()) != null) {
                if (signal.startsWith("Start Call ")) {
                    msisdn = signal.replace("Start Call ", "").trim();
                    currentBalance = getFinalBalance(msisdn);
                    if (currentBalance <= 0) {
                        System.out.println("Reject Call from MSISDN " + msisdn + ": Insufficient balance (" + currentBalance + ")");
                        out.println("REJECT");
                        break;
                    }
                    System.out.println("Accept Voice call start signaling message from MSISDN " + msisdn + ", on UDP port:" + udpPort + " with balance: " + currentBalance);
					out.println("PORT " + udpPort);
                    startTime = LocalDateTime.now();
                    callActive = true;
                    elapsedMinutes = 0;
                    MSC.activeSessions.put(msisdn, new CallSessionInfo(msisdn, startTime.toString(), elapsedMinutes, udpPort, "SIP/CLI", currentBalance));

                    new Thread(this::WriteAudioFile).start();
                    new Thread(this::handleRealTimeCharging).start();
                    
                } else if (signal.equals("End Call")) {
                    callActive = false;
                    System.out.println("Call End after receiving end call signaling message from MSISDN " + msisdn + ", on udp port: " + udpPort );
                    generateCDR("User Hang Up");
                    break;
                }
            }
            if (callActive) {
                callActive = false;
                System.out.println("Call End after connection drop from MSISDN " + msisdn + ", on udp port: " + udpPort);
                generateCDR("Connection Lost");
            } else if (callResultCode != null) {
                generateCDR(null);
            }
        } catch (Exception e) {
            System.err.println("[Call " + msisdn + "] Connection error: " + e.getMessage());
            if (callActive) {
                callActive = false;
                generateCDR("Connection Lost");
            } else if (callResultCode != null) {
                generateCDR(null);
            }
        } finally {
            if (msisdn != null && !msisdn.isEmpty()) {
                MSC.activeSessions.remove(msisdn);
            }
        }
	}
	
    private void WriteAudioFile() {
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd");
        String dateStr = startTime.format(dateFormatter);
		DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH_mm_ss");
        String timeStr = startTime.format(timeFormatter);
		String path = String.format("/tmp/voice_call_msisdn_%s_date_%s_Time_%s.wav", msisdn, dateStr, timeStr);
        try (DatagramSocket socket = new DatagramSocket(udpPort)) {
            socket.setSoTimeout(1000);
            ByteArrayOutputStream rawAudio = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE + RTP_HEADER_SIZE];
            int lastSequenceNumber = -1;

            while (callActive) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    int packetLength = packet.getLength();
                    
                    if (packetLength <= RTP_HEADER_SIZE) {
                        continue;
                    }
                    
                    byte[] data = packet.getData();
                    int seq = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    
                    if (lastSequenceNumber != -1) {
                        int diff = seq - lastSequenceNumber - 1;
                        if (diff < -32768) {
                            diff += 65536;
                        }
                        if (diff > 0 && diff < 100) {
                            int audioLength = packetLength - RTP_HEADER_SIZE;
                            byte[] silence = new byte[audioLength];
                            for (int i = 0; i < diff; i++) {
                                rawAudio.write(silence);
                            }
                            System.out.println("[Call " + msisdn + "] Packet loss detected: lost " + diff + " packet(s). Inserted silence PCM.");
                        }
                    }
                    lastSequenceNumber = seq;
                    
                    int audioLength = packetLength - RTP_HEADER_SIZE;
                    rawAudio.write(packet.getData(), RTP_HEADER_SIZE, audioLength);
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
            System.out.println("Audio saved to " + path + " (" + audioData.length + " bytes) for call " + msisdn);

        } catch (Exception e) {
            System.err.println("[Call " + msisdn + "] Audio Writer error: " + e.getMessage());
        }
    }

    private void handleRealTimeCharging() {
        while (callActive) {
            try {
                elapsedMinutes++;
                deductBalance(msisdn, CHARGE_PER_MINUTE);
                currentBalance -= CHARGE_PER_MINUTE;
                
                CallSessionInfo info = MSC.activeSessions.get(msisdn);
                if (info != null) {
                    info.setElapsedMinutes(elapsedMinutes);
                    info.setCurrentBalance(currentBalance);
                }

                if (currentBalance <= 0) {
                    System.out.println("[Call " + msisdn + "] Balance exhausted. Disconnecting call...");
                    callActive = false;
                    this.callResultCode = "Depleted";
                    try {
                        tcpSocket.close();
                    } catch (Exception ignored) {}
                    break;
                }
                
                Thread.sleep(60000);
                if (!callActive)
                    break;
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void deductBalance(String targetMsisdn, double amount) {
        String query = "UPDATE Users SET Balance = Balance - ? WHERE MSISDN = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, amount);
            ps.setString(2, targetMsisdn);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[Call " + msisdn + "] DB Charging Error: " + e.getMessage());
        }
    }

    private double getFinalBalance(String targetMsisdn) {
        String query = "SELECT Balance FROM Users WHERE MSISDN = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, targetMsisdn);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getDouble("Balance");
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    private void generateCDR(String resultOverride) {
        LocalDateTime endTime = LocalDateTime.now();
        double cost = elapsedMinutes * CHARGE_PER_MINUTE;
        String callResult;
        if (resultOverride != null) {
            callResult = resultOverride;
        } else if (callResultCode != null) {
            callResult = callResultCode;
        } else if (elapsedMinutes == 0) {
            callResult = "Cancelled";
        } else {
            callResult = "Normal call Clearing";
        }

        // one transaction: read final balance + write CDR atomically.
        // if either step fails, both roll back — no charge without a CDR record.
        String selectQuery = "SELECT Balance FROM Users WHERE MSISDN = ?";
        String insertQuery = "INSERT INTO CDRs (msisdn, start_time, end_time, duration_mins, cost, result, final_balance) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                double finalBalance = 0.0;
                try (PreparedStatement ps = conn.prepareStatement(selectQuery)) {
                    ps.setString(1, msisdn);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) finalBalance = rs.getDouble("Balance");
                }
                try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                    ps.setString(1, msisdn);
                    ps.setTimestamp(2, java.sql.Timestamp.valueOf(startTime));
                    ps.setTimestamp(3, java.sql.Timestamp.valueOf(endTime));
                    ps.setInt(4, elapsedMinutes);
                    ps.setDouble(5, cost);
                    ps.setString(6, callResult);
                    ps.setDouble(7, finalBalance);
                    ps.executeUpdate();
                }
                conn.commit();

                // Format required pattern: MSISDN, Start, End, Duration, Result, Cost, Balance
                String cdrLine = String.format("%s,%s,%s,%d,%s,%.0f,%.0f",
                        msisdn, startTime, endTime, elapsedMinutes, callResult, cost, finalBalance);
                System.out.println("Generating CDR line: " + cdrLine);
                cdrLogger.info(cdrLine);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.err.println("[Call " + msisdn + "] DB CDR Write Error: " + e.getMessage());
        }
    }
}
