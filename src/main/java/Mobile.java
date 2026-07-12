import javax.sound.sampled.*;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class Mobile {
    private static final String MSC_IP = "localhost";
    private static final int TCP_PORT = 8888;
    private static final int RTP_PORT = 5004;
    private static final int RTP_HEADER_SIZE = 12;
    private static final int BUFFER_SIZE = 1024;

    private static volatile boolean isCalling = true;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Mobile <MSISDN>");
            return;
        }
        String msisdn = args[0];

        System.out.println("Starting voice call as MSISDN " + msisdn);
        
        try {
            Socket tcpSocket = new Socket(MSC_IP, TCP_PORT);
            PrintWriter out = new PrintWriter(tcpSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            out.println("Start Call " + msisdn);
            
            String response = in.readLine();
            int assignedUdpPort = RTP_PORT;
            
            if (response != null && response.startsWith("PORT ")) {
            	assignedUdpPort = Integer.parseInt(response.split(" ")[1]);
            	System.out.println("Assigned RTP UDP Port: " + assignedUdpPort);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    isCalling = false;
                    out.println("End Call");
                    tcpSocket.close();
                    System.out.println("\nCall ended by user.");
                } catch (Exception e) {
                    System.err.println("Error closing signaling: " + e.getMessage());
                }
            }));
            // Start TCP signaling listener thread to detect server-side hangup/disconnect
            new Thread(() -> {
                try {
                    String sig;
                    while (isCalling && (sig = in.readLine()) != null) {
                        if (sig.equals("REJECT")) {
                            System.out.println("\nCall rejected by server (insufficient balance).");
                            isCalling = false;
                            System.exit(0);
                        }
                    }
                    if (isCalling) {
                        System.out.println("\nCall disconnected by server.");
                        isCalling = false;
                        System.exit(0);
                    }
                } catch (Exception e) {
                    if (isCalling) {
                        System.out.println("\nCall signaling connection lost.");
                        isCalling = false;
                        System.exit(0);
                    }
                }
            }).start();

            System.out.println("Capturing Voice from Microphone and send via UDP...");

            // 3. Start Minute Counter Thread
            new Thread(() -> {
                int minutes = 0;
                while (isCalling) {
                    try {
                        Thread.sleep(60000); // 1 minute
                        minutes++;
                        System.out.println(minutes + " minutes elapsed");
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();

            // 4. Voice Streaming over RTP
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            try (DatagramSocket udpSocket = new DatagramSocket();
                    TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info)) {

                line.open(format);
                line.start();
                
                byte[] buffer = new byte[BUFFER_SIZE];
                InetAddress mscAddress = InetAddress.getByName(MSC_IP);
		
                int sequenceNumber = 0;
        	long timestamp = 0;
        	int ssrc = 12345;
		
                while (isCalling) {
                    int bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                    	byte[] rtpPacket = new byte[RTP_HEADER_SIZE + bytesRead];
	            	// RTP Header
		    	rtpPacket[0] = (byte) 0x80; // Version 2
		    	rtpPacket[1] = (byte) 0x0B;   // Payload type

		    	// Sequence Number
		    	rtpPacket[2] = (byte) (sequenceNumber >> 8);
		    	rtpPacket[3] = (byte) (sequenceNumber);

		    	// Timestamp
		    	rtpPacket[4] = (byte) (timestamp >> 24);
		    	rtpPacket[5] = (byte) (timestamp >> 16);
		    	rtpPacket[6] = (byte) (timestamp >> 8);
		    	rtpPacket[7] = (byte) (timestamp);

		    	// SSRC
		    	rtpPacket[8] = (byte) (ssrc >> 24);
		    	rtpPacket[9] = (byte) (ssrc >> 16);
		    	rtpPacket[10] = (byte) (ssrc >> 8);
		    	rtpPacket[11] = (byte) (ssrc);
		    	
		    	System.arraycopy(buffer, 0, rtpPacket, RTP_HEADER_SIZE, bytesRead);
		    	
                        DatagramPacket packet = new DatagramPacket(rtpPacket, rtpPacket.length, mscAddress, assignedUdpPort);
                        udpSocket.send(packet);
                        
                        sequenceNumber++;
            		timestamp += bytesRead / 2;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error during call execution: " + e.getMessage());
        }
    }
}
