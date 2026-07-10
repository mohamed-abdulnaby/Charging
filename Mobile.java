import javax.sound.sampled.*;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class Mobile {
    private static final String MSC_IP = "localhost";
    private static final int TCP_PORT = 8888;
    private static final int UDP_PORT = 9876;
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
            out.println("Start Call " + msisdn);

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

            // 4. Voice Streaming over UDP
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            try (DatagramSocket udpSocket = new DatagramSocket();
                    TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info)) {

                line.open(format);
                line.start();
                byte[] buffer = new byte[BUFFER_SIZE];
                InetAddress mscAddress = InetAddress.getByName(MSC_IP);

                while (isCalling) {
                    int bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        DatagramPacket packet = new DatagramPacket(buffer, bytesRead, mscAddress, UDP_PORT);
                        udpSocket.send(packet);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error during call execution: " + e.getMessage());
        }
    }
}
