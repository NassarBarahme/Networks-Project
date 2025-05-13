package proj;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    public static final int DEFAULT_PORT = 8888;
    private static final String USER_CREDENTIALS_FILE = "user_credentials.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private ServerSocket serverSocket;
    private ServerSocket fileServerSocket;
    private ExecutorService executorService;
    private Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();
    private Map<String, String> userCredentials = new ConcurrentHashMap<>();
    private ServerGUI gui;
    private FileTransferManager fileTransferManager;

    public Server(ServerGUI gui) {
        this.gui = gui;
        loadUserCredentials();
    }

    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            fileServerSocket = new ServerSocket(port + 1);
            executorService = Executors.newCachedThreadPool();

            // Thread for chat connections
            new Thread(this::handleChatConnections).start();

            // Thread for file transfers
            new Thread(this::handleFileConnections).start();

            log("Server started on port " + port + " (files on " + (port + 1) + ")");
        } catch (IOException e) {
            log("Error starting server: " + e.getMessage());
        }
    }

    private void handleChatConnections() {
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                executorService.execute(handler);
                log("New client connected: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }



    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (fileServerSocket != null && !fileServerSocket.isClosed()) {
                fileServerSocket.close();
            }
            if (executorService != null) {
                executorService.shutdownNow();
            }
            log("Server stopped");
        } catch (IOException e) {
            log("Error stopping server: " + e.getMessage());
        }
    }

    private void loadUserCredentials() {
        File credFile = new File(USER_CREDENTIALS_FILE);
        if (!credFile.exists()) {
            try {
                credFile.createNewFile();
            } catch (IOException e) {
                log("Error creating credentials file: " + e.getMessage());
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(USER_CREDENTIALS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 2) {
                    userCredentials.put(parts[0].trim().toLowerCase(), parts[1].trim());
                }
            }
            log("Loaded " + userCredentials.size() + " user credentials");
        } catch (IOException e) {
            log("Error loading user credentials: " + e.getMessage());
        }
    }

    private void saveUserCredentials() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_CREDENTIALS_FILE))) {
            for (Map.Entry<String, String> entry : userCredentials.entrySet()) {
                writer.println(entry.getKey() + "|" + entry.getValue());
            }
            log("User credentials saved (" + userCredentials.size() + " users)");
        } catch (IOException e) {
            log("Error saving user credentials: " + e.getMessage());
        }
    }

    public boolean addUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }

        if(userCredentials.containsKey(username.toLowerCase().trim())) {
            appendStatus("Username already exists");
            return false;
        }

        userCredentials.put(username.toLowerCase().trim(), password.trim());
        saveUserCredentials();
        return true;
    }

    public boolean removeUser(String username) {
        if (username == null || !userCredentials.containsKey(username.toLowerCase())) {
            return false;
        }
        userCredentials.remove(username.toLowerCase());
        saveUserCredentials();
        return true;
    }

    private void log(String message) {
        gui.log(message);
    }

    private void appendStatus(String message) {
        gui.appendStatus(message);
    }

    public void updateClientList() {
        gui.updateClientList(activeClients.keySet());
    }

    private void broadcastClientList() {
        StringBuilder clientListStr = new StringBuilder("CLIENT_LIST:");
        for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
            clientListStr.append(entry.getKey()).append("|").append(entry.getValue().getStatus()).append(",");
        }

        String listMessage = clientListStr.toString();
        for (ClientHandler handler : activeClients.values()) {
            handler.sendMessage(listMessage);
        }
    }

    public boolean isRunning() {
        return serverSocket != null && !serverSocket.isClosed();
    }

    public List<String> getActiveUsers() {
        return new ArrayList<>(activeClients.keySet());
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String status = "Active";
        private String lastLoginTime;
        private int fileTransferPort;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.fileTransferPort = fileServerSocket.getLocalPort();
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Authentication
                String credentials = in.readLine();
                if (credentials == null) return;

                String[] credParts = credentials.split(":");
                if (credParts.length != 2) {
                    out.println("INVALID_CREDENTIALS");
                    return;
                }

                String inputUsername = credParts[0].trim().toLowerCase();
                String inputPassword = credParts[1].trim();

                if (!userCredentials.containsKey(inputUsername) ||
                        !userCredentials.get(inputUsername).equals(inputPassword)) {
                    out.println("INVALID_CREDENTIALS");
                    return;
                }

                this.username = inputUsername;
                this.lastLoginTime = DATE_FORMAT.format(new Date());
                out.println("LOGIN_SUCCESS:" + lastLoginTime);
                out.println("FILE_PORT:" + fileTransferPort); // إرسال منفذ الملفات

                // Get client info
                String clientInfo = in.readLine();
                if (clientInfo == null) return;

                String clientKey = username + " - " + clientInfo;
                activeClients.put(clientKey, this);
                updateClientList();
                broadcastClientList();

                log(username + " connected: " + clientInfo);

                // Handle client messages
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("STATUS:")) {
                        handleStatusChange(message.substring(7));
                    } else if (message.startsWith("SEND_FILE:")) {
                        handleFileTransferRequest(message.substring(10));
                    } else if (message.equals("LOGOUT")) {
                        break;
                    } else {
                        // Handle other messages
                        broadcastMessage(username + ": " + message);
                    }
                }
            } catch (IOException e) {
                log("Error with client " + (username != null ? username : "unknown") + ": " + e.getMessage());
            } finally {
                disconnectClient();
            }
        }

        private void handleFileTransferRequest(String filePath) {
            File file = new File(filePath);
            if (!file.exists()) {
                out.println("FILE_ERROR:File not found");
                return;
            }

            if (file.length() > 100 * 1024 * 1024) { // 100MB limit
                out.println("FILE_ERROR:File size exceeds 100MB limit");
                return;
            }

            // إرسال معلومات الملف كنص
            out.println("FILE_START:" + file.getName() + ":" + file.length());

            try {
                // استخدام OutputStream منفصل لإرسال البيانات الثنائية
                OutputStream dataOut = socket.getOutputStream();
                FileInputStream fis = new FileInputStream(file);

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }

                fis.close();

                // إرسال إشعار انتهاء الإرسال كنص
                out.println("FILE_COMPLETE:" + file.getName());
                log(username + " successfully sent file: " + file.getName());
            } catch (IOException e) {
                out.println("FILE_ERROR:" + e.getMessage());
                log("File transfer error for " + username + ": " + e.getMessage());
            }
        }

        private void handleStatusChange(String newStatus) {
            this.status = newStatus;
            broadcastClientList();
            log(username + " status changed to: " + status);
        }


        private void disconnectClient() {
            if (username != null) {
                String clientKey = username + " - " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                activeClients.remove(clientKey); // يزيل العميل من القائمة

                updateClientList(); // يُحدّث واجهة السيرفر
                broadcastClientList(); // يُرسل القائمة المحدثة للجميع

                log(username + " disconnected");
            }
            try {
                socket.close();
            } catch (IOException e) {
                log("Error closing socket: " + e.getMessage());
            }
        }

        private void broadcastMessage(String message) {
            for (ClientHandler handler : activeClients.values()) {
                if (!handler.username.equals(this.username)) {
                    handler.sendMessage("MESSAGE:" + message);
                }
            }
        }


        public void sendMessage(String message) {
            out.println(message);
        }

        public String getStatus() {
            return status;
        }
    }

    private void handleFileConnections() {
        try {
            // إنشاء مجلد لحفظ الملفات المُستلمة إن لم يكن موجودًا
            File saveDirectory = new File("received_files");
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs();
            }

            // بدء الاستماع للملفات القادمة باستخدام FileTransferManager
            FileTransferManager.receiveFile(fileServerSocket, saveDirectory, gui);

            log("File transfer handler started (listening on port " + fileServerSocket.getLocalPort() + ")");
        } catch (Exception e) {
            log("Error in handleFileConnections: " + e.getMessage());
        }
    }




}