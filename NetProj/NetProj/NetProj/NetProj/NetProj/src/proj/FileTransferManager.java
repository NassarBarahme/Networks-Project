package proj;

import java.io.*;
import java.net.*;
import java.text.DecimalFormat;

public class FileTransferManager {
    private static final int BUFFER_SIZE = 8192;
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.00");

    public static void sendFile(File file, String receiverIp, int receiverPort, FileTransferListener listener) throws IOException {
        long startTime = System.currentTimeMillis();
        long fileSize = file.length();

        try (Socket socket = new Socket(receiverIp, receiverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            dos.writeUTF(file.getName());
            dos.writeLong(fileSize);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalSent = 0;
            int progress = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalSent += bytesRead;

                int newProgress = (int) ((totalSent * 100) / fileSize);
                if (newProgress != progress) {
                    progress = newProgress;
                    listener.appendStatus("Sending file: " + progress + "% (" +
                            formatFileSize(totalSent) + " / " + formatFileSize(fileSize) + ")");
                }
            }

            long endTime = System.currentTimeMillis();
            double durationSec = (endTime - startTime) / 1000.0;
            double speed = (fileSize / 1024.0) / durationSec;

            listener.appendChatMessage("[File Transfer] Sent '" + file.getName() + "' (" +
                    formatFileSize(fileSize) + " in " + durationSec + " sec)");
            listener.appendChatMessage("[Transfer Speed] " + SIZE_FORMAT.format(speed) + " KB/s");
        }
    }

    public static void receiveFile(ServerSocket serverSocket, File saveDirectory, FileTransferListener listener) {
        new Thread(() -> {
            while (true) {
                try (Socket socket = serverSocket.accept();
                     DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();

                    File file = new File(saveDirectory, fileName);
                    int counter = 1;
                    while (file.exists()) {
                        String newName = fileName.substring(0, fileName.lastIndexOf('.')) +
                                "_" + counter +
                                fileName.substring(fileName.lastIndexOf('.'));
                        file = new File(saveDirectory, newName);
                        counter++;
                    }

                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long totalReceived = 0;
                        int progress = 0;
                        long startTime = System.currentTimeMillis();

                        while (totalReceived < fileSize && (bytesRead = dis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalReceived += bytesRead;

                            int newProgress = (int) ((totalReceived * 100) / fileSize);
                            if (newProgress != progress) {
                                progress = newProgress;
                                listener.appendStatus("Receiving file: " + progress + "% (" +
                                        formatFileSize(totalReceived) + " / " + formatFileSize(fileSize) + ")");
                            }
                        }

                        long endTime = System.currentTimeMillis();
                        double durationSec = (endTime - startTime) / 1000.0;

                        listener.appendChatMessage("[File Received] '" + file.getName() + "' (" +
                                formatFileSize(fileSize) + " in " + durationSec + " sec)");
                        listener.appendStatus("File saved to: " + file.getAbsolutePath());
                    }
                } catch (IOException e) {
                    listener.appendStatus("Error receiving file: " + e.getMessage());
                }
            }
        }).start();
    }

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return SIZE_FORMAT.format(size / 1024.0) + " KB";
        if (size < 1024 * 1024 * 1024) return SIZE_FORMAT.format(size / (1024.0 * 1024)) + " MB";
        return SIZE_FORMAT.format(size / (1024.0 * 1024 * 1024)) + " GB";
    }
}