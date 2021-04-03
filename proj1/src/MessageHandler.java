import java.net.InetAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MessageHandler implements Runnable {
    private MessageParser messageParser;
    private String peerID;
    private InetAddress senderAddress;

    public MessageHandler(byte[] message, String peerID, InetAddress senderAddress) {
        this.peerID = peerID;
        this.messageParser = new MessageParser(message);
        this.senderAddress = senderAddress;
    }

    @Override
    public void run() {
        // Checking Parsing
        if (!this.messageParser.parse()) {
            System.out.println("Error parsing message");
            return;
        }

        // Ignore self-messages
        if(messageParser.getSenderID().equals(this.peerID)) {
            return;
        }

        switch (messageParser.getMessageType()) {
            case "PUTCHUNK" -> handlePUTCHUNK();
            case "STORED" -> handleSTORED();
            case "GETCHUNK" -> handleGETCHUNK();
            case "CHUNK" -> handleCHUNK();
            case "DELETE" -> handleDELETE();
            case "REMOVED" -> handleREMOVED();
            default -> System.out.println("Invalid message type received: " + messageParser.getMessageType());
        }
    }

    private void handlePUTCHUNK() {
        System.out.println("MessageHandler receiving :: PUTCHUNK chunk " + this.messageParser.getChunkNo() + " Sender " + this.messageParser.getSenderID());
        Random delay = new Random();
        Peer.executor.schedule(new Thread(() -> {
            Chunk chunk = new Chunk(this.messageParser.getVersion(), this.messageParser.getFileID(), this.messageParser.getChunkNo(), this.messageParser.getReplicationDegree(), this.messageParser.getBody());
            Peer.getData().storeNewChunk(chunk);
            }), delay.nextInt(401), TimeUnit.MILLISECONDS
        );
    }

    private void handleSTORED() {
        System.out.println("MessageHandler receiving :: STORED chunk " + this.messageParser.getChunkNo() + " Sender " + this.messageParser.getSenderID());
        Peer.getData().updateChunkReplicationsNum(this.messageParser.getFileID(), this.messageParser.getChunkNo(), this.messageParser.getSenderID());
    }

    private void handleGETCHUNK() {
        System.out.println("MessageHandler receiving :: GETCHUNK chunk " + this.messageParser.getChunkNo() + " Sender " + this.messageParser.getSenderID());
        String chunkID = this.messageParser.getFileID() + "-" + this.messageParser.getChunkNo();
        Peer.getData().removeChunkMessagesSent(chunkID);
        Random delay = new Random();
        ChunkThread chunkThread = new ChunkThread(this.messageParser.getSenderID(), this.messageParser.getFileID(), this.messageParser.getChunkNo(), this.senderAddress, this.messageParser.getPort());
        Peer.executor.schedule(chunkThread,delay.nextInt(401), TimeUnit.MILLISECONDS);
    }

    private void handleCHUNK() {
        System.out.println("MessageHandler receiving :: CHUNK chunk " + this.messageParser.getChunkNo() + " Sender " + this.messageParser.getSenderID());
        String chunkID = this.messageParser.getFileID() + "-" + this.messageParser.getChunkNo();

        if(Peer.getData().hasWaitingChunk(chunkID)) {
            if (Peer.getVersion().equals("1.0")) {
                Peer.getData().removeWaitingChunk(chunkID);
                int replicationDegree = Peer.getData().getFileReplicationDegree(this.messageParser.getFileID());
                Peer.getData().addReceivedChunk(new Chunk(this.messageParser.getVersion(), this.messageParser.getFileID(), this.messageParser.getChunkNo(), replicationDegree, this.messageParser.getBody()));
            }
        }
        else {
            Peer.getData().addChunkMessagesSent(chunkID);
        }
    }

    private void handleDELETE() {
        System.out.println("MessageHandler receiving :: DELETE chunk " + this.messageParser.getChunkNo() + " Sender " + this.messageParser.getSenderID());

        if (!Peer.getData().deleteFileChunks(this.messageParser.getFileID())) {
            System.out.println("Error on operation DELETE");
        }
        Peer.getData().resetPeersBackingUp(this.messageParser.getFileID());
    }

    private void handleREMOVED() {
        System.out.println("MessageHandler receiving :: REMOVED chunk " + this.messageParser.getChunkNo() + " Sender " + this.messageParser.getSenderID());
        // Verificar se tem uma cópia local do chunk
        String chunkID = this.messageParser.getFileID() + "-" + this.messageParser.getChunkNo();
        Peer.getData().removePeerBackingUpChunk(chunkID, this.messageParser.getSenderID());

        if (Peer.getData().hasFileData(this.messageParser.getFileID())) {
            FileData filedata = Peer.getData().getFileData(this.messageParser.getFileID());
            int currentRepDegree = Peer.getData().getFileReplicationDegree(this.messageParser.getFileID());
            int desiredRepDegree = filedata.getReplicationDegree();
            System.out.println("Current Degree: " + currentRepDegree);
            System.out.println("Desired Degree: " + desiredRepDegree);
            if (currentRepDegree < desiredRepDegree) {
                System.out.println(this.messageParser.getSenderID());
                System.out.println(Peer.getPeerID());
                byte[] message = MessageParser.makeMessage(this.messageParser.getBody(), this.messageParser.getVersion(), "PUTCHUNK", Peer.getPeerID(), this.messageParser.getFileID(), Integer.toString(this.messageParser.getChunkNo()), Integer.toString(desiredRepDegree));
                Random delay = new Random();

                PutChunkThread putChunkThread = new PutChunkThread(message, this.messageParser.getFileID(), this.messageParser.getChunkNo(), desiredRepDegree);
                Peer.executor.schedule(putChunkThread,delay.nextInt(401), TimeUnit.MILLISECONDS);
            }
        }
    }
}