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
    private static int nextUdpPort = 10000;

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
    	return nextUdpPort++;
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
    private LocalDateTime startTime;
    private int elapsedMinutes = 0;

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
                    System.out.println("Accept Voice call start signaling message from MSISDN " + msisdn + ", on UDP port:" + udpPort);
					out.println("PORT " + udpPort);
                    startTime = LocalDateTime.now();
                    callActive = true;
                    elapsedMinutes = 0;

                    new Thread(this::WriteAudioFile).start();
                    new Thread(this::handleRealTimeCharging).start();
                    
                } else if (signal.equals("End Call")) {
                    callActive = false;
                    System.out.println("Call End after receiving end call signaling message from MSISDN " + msisdn + ", on udp port: " + udpPort );
                    generateCDR();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[Call " + msisdn + "] Connection error: " + e.getMessage());
            callActive = false;
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

            while (callActive) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    int packetLength = packet.getLength();
                    
                    if (packetLength <= RTP_HEADER_SIZE) {
                        continue;
                    }
                    
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

    private void generateCDR() {
        LocalDateTime endTime = LocalDateTime.now();
        double cost = elapsedMinutes * CHARGE_PER_MINUTE;
        double finalBalance = getFinalBalance(msisdn);
        String callResult = (finalBalance == 0.0 && cost > 0) ? "user not found on DB" : "Normal call Clearing";

        // Format required pattern: MSISDN, Start, End, Duration, Result, Cost, Balance
        String cdrLine = String.format("%s,%s,%s,%d,%s,%.0f,%.0f",
                msisdn, startTime, endTime, elapsedMinutes, callResult, cost, finalBalance);

        System.out.println("Generating CDR line: " + cdrLine);
        cdrLogger.info(cdrLine);
    }
}

