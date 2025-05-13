package proj;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private Socket tcpSocket;
    private PrintWriter tcpOut;
    private BufferedReader tcpIn;
    private DatagramSocket udpSocket;
    private String username;
    private String lastLoginTime;
    private String status = "Active";
    private List<String> archivedMessages = new ArrayList<>();
    private boolean isLoggedIn = false;

    private ClientGUI gui;
    private int localPort;
    private String localIp;

    public Client(ClientGUI gui) {
        this.gui = gui;
    }


    public void login(String username, String password, String serverIp, int serverPort,
                      String localIp, int localPort) throws IOException {

        tcpSocket = new Socket(serverIp, serverPort);
        tcpOut = new PrintWriter(tcpSocket.getOutputStream(), true);
        tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));

        tcpOut.println(username + ":" + password);
        String response = tcpIn.readLine();

        if (response == null || response.startsWith("INVALID_CREDENTIALS")) {
            throw new IOException("Invalid login information");
        }
        this.localIp=localIp;
        this.localPort=localPort;
        this.username = username;
        this.lastLoginTime = response.split(":")[1];

        tcpOut.println(localIp + ":" + localPort);

        // Start UDP listener for messages
        startUDPListener(localPort);

        // Start file receiver on port+1
        ServerSocket fileSocket = new ServerSocket(localPort + 1);
        FileTransferManager.receiveFile(fileSocket, new File("received_files"), gui);

        new Thread(this::listenForServerMessages).start();
        isLoggedIn = true;
    }

    public void logout() {
        if (isLoggedIn && tcpOut != null) {
            tcpOut.println("LOGOUT");
        }

        try {
            if (tcpSocket != null && !tcpSocket.isClosed()) {
                tcpSocket.close();
            }
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }
        } catch (IOException e) {
            gui.appendStatus("Error during logout: " + e.getMessage());
        }

        isLoggedIn = false;
        username = null;
        tcpSocket = null;
        tcpOut = null;
        tcpIn = null;
        udpSocket = null;
    }

    private void startUDPListener(int port) throws SocketException {
        udpSocket = new DatagramSocket(port);

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (!udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String receivedMessage = new String(packet.getData(), 0, packet.getLength());
                    String timestamp = DATE_FORMAT.format(new Date());
                    String senderInfo = packet.getAddress().getHostAddress() + ":" + packet.getPort();

                    gui.appendChatMessage("[" + timestamp + "] Received from " + senderInfo + ": " + receivedMessage);
                } catch (SocketException e) {
                    if (!udpSocket.isClosed()) {
                        gui.appendStatus("UDP Socket error: " + e.getMessage());
                    }
                    break;
                } catch (IOException e) {
                    gui.appendStatus("Error receiving UDP message: " + e.getMessage());
                }
            }
        }).start();
    }

    private void listenForServerMessages() {
        try {
            String message;
            while ((message = tcpIn.readLine()) != null) {
                if (message.startsWith("CLIENT_LIST:")) {
                    gui.updateOnlineUsersList(message.substring(12));
                }
            }
        } catch (IOException e) {
            if (!tcpSocket.isClosed()) {
                gui.appendStatus("Error reading from server: " + e.getMessage());
            }
        }
    }

    public void sendMessage(String message, String remoteIp, int remotePort) throws IOException {
        if (!isLoggedIn || udpSocket == null || udpSocket.isClosed()) {
            throw new IOException("Not connected or UDP socket not initialized");
        }

        InetAddress remoteAddress = InetAddress.getByName(remoteIp);
        byte[] messageBytes = message.getBytes();
        DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, remoteAddress, remotePort);
        udpSocket.send(packet);

        String timestamp = DATE_FORMAT.format(new Date());
        gui.appendChatMessage("[" + timestamp + "] Sent to " + remoteIp + ":" + remotePort + ": " + message);
    }

    public void testConnection(String remoteIp, int remotePort) throws IOException {
        if (!isLoggedIn || udpSocket == null || udpSocket.isClosed()) {
            throw new IOException("Not connected or UDP socket not initialized");
        }

        InetAddress remoteAddress = InetAddress.getByName(remoteIp);
        String testMessage = "TEST_CONNECTION";
        byte[] messageBytes = testMessage.getBytes();
        DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, remoteAddress, remotePort);
        udpSocket.send(packet);
    }

    public void setStatus(String status) {
        this.status = status;
        if (isLoggedIn && tcpOut != null) {
            tcpOut.println("STATUS:" + status);
        }
    }

    public String getStatus() {
        return status;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public String getLastLoginTime() {
        return lastLoginTime;
    }

    public String getUsername() {
        return username;
    }


    public List<String> getArchivedMessages() {
        return archivedMessages;
    }

    public void sendFile(File file, String receiverIp, int receiverPort) throws IOException {
        if (!isLoggedIn) {
            throw new IOException("غير متصل بالخادم");
        }

        // إنشاء اتصال مباشر مع المستلم
        try (Socket socket = new Socket(receiverIp, receiverPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            // إرسال معلومات الملف
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            // إرسال محتوى الملف
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
        }
    }


    public void broadcastMessage(String message, Map<String, String> onlineUsers) throws IOException {
        if (!isLoggedIn || udpSocket == null || udpSocket.isClosed()) {
            throw new IOException("Not connected or UDP socket not initialized");
        }

        for (Map.Entry<String, String> entry : onlineUsers.entrySet()) {
            String username = entry.getKey();
            String address = entry.getValue(); // 192.168.1.7:4567

            String[] parts = address.split(":");
            if (parts.length != 2) continue;

            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            // تجنب إرسال الرسالة لنفس الكلاينت
            if (ip.equals(this.localIp) && port == this.localPort) continue;

            InetAddress remoteAddress = InetAddress.getByName(ip);
            byte[] messageBytes = message.getBytes();
            DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, remoteAddress, port);
            udpSocket.send(packet);
        }

        String timestamp = DATE_FORMAT.format(new Date());
        gui.appendChatMessage("[" + timestamp + "] Broadcast: " + message);
    }

    public void setArchive(String content) {
        System.out.println("Setting archive to: " +
                (content.isEmpty() ? "EMPTY" : content.length() + " chars"));
        // Clear previous archive before adding new content
        archivedMessages.clear();
        if (!content.isEmpty()) {
            archivedMessages.add(content);
        }
    }

    public String getArchive() {
        System.out.println("Getting archive: " +
                (archivedMessages.isEmpty() ? "EMPTY" : archivedMessages.size() + " items"));
        // Return joined string if you need the full content
        return String.join("\n", archivedMessages);
    }

    // Add this method to completely clear the archive
    public void clearArchive() {
        archivedMessages.clear();
        System.out.println("Archive completely cleared");
    }
}