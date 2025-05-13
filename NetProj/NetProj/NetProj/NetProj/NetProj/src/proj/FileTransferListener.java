package proj;

public interface FileTransferListener {
    void appendStatus(String message);
    void appendChatMessage(String message);
}