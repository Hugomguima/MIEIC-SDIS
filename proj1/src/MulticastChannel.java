import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class MulticastChannel implements Runnable {
    protected final InetAddress addr;
    protected final int port;
    protected final MulticastSocket multicastSocket;
    protected final String peerID;

    MulticastChannel(InetAddress addr, int port, String peerID) throws IOException {
        this.addr = addr;
        this.port = port;
        this.peerID = peerID;
        this.multicastSocket = new MulticastSocket(port);
        this.multicastSocket.joinGroup(this.addr);
    }

    void sendMessage(byte[] message) {
        try {
            DatagramPacket packetSend = new DatagramPacket(message, message.length, this.addr, this.port);
            this.multicastSocket.send(packetSend);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error sending message to Multicast Data Channel (MDB)");
        }
    }

    @Override
    public void run() {
        byte[] msgReceived = new byte[65507]; // maximum data size for UDP packet -> https://en.wikipedia.org/wiki/User_Datagram_Protocol

        try {
            while(true) {
                // Waiting to receive a packet
                DatagramPacket requestPacket = new DatagramPacket(msgReceived, msgReceived.length);
                this.multicastSocket.receive(requestPacket);
                Peer.executor.execute(new MessageHandler(requestPacket.getData(), this.peerID, requestPacket.getAddress()));
            }
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error receiving message from Multicast Data Channel (MDB)");
        }
    }

    protected String createId(String peerID, String filename, long dateModified) {
        return sha256(peerID + "//" + filename + "//" + dateModified);
    }

    // https://stackoverflow.com/questions/5531455/how-to-hash-some-string-with-sha256-in-java
    public static String sha256(String base) {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch(Exception ex){
            throw new RuntimeException(ex);
        }
    }
}