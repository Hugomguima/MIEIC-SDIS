package g24.protocol;

import java.io.DataOutputStream;

import g24.storage.FileData;
import g24.storage.Storage;

public class Backup extends Handler {
    private String fileID;
    private byte[] data;
    private Storage storage;

    public Backup(String fileID, byte[] data, Storage storage) {
        this.fileID = fileID;
        this.data = data;
        this.storage = storage;
    }

    @Override
    public void run() {
        
            try {
                if(!this.storage.hasFileStored(this.fileID)) {
                    FileData newFileData = new FileData(this.fileID, this.data);
                    this.storage.store(newFileData);
                }
                
                byte[] message = ("OK").getBytes();
                out.write(message, 0, message.length);
                out.flush();
                out.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
    }
}
