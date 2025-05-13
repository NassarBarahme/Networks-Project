package proj;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;


public class ClientGUI implements FileTransferListener  {
    private static final Color PRIMARY_COLOR   = new Color(33, 150, 243);    // Blue 500
    private static final Color SECONDARY_COLOR = new Color(250, 250, 250);   // Light Gray
    private static final Color ACCENT_COLOR    = new Color(25, 118, 210);    // Blue 700
    private static final Color ERROR_COLOR     = new Color(244, 67, 54);     // Red 500
    private static final Color SUCCESS_COLOR   = new Color(76, 175, 80);     // Green 500
    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, logoutButton;
    private JTextField tcpServerIpField, tcpServerPortField;
    private JComboBox<String> interfaceComboBox;
    private JTextField localIpField, localPortField, remoteIpField, remotePortField;
    private JTextArea chatArea, statusArea;
    private JTextField messageField;
    private JButton sendButton, clearButton, archiveButton, exportButton, testButton;
    private JList<String> onlineUsersList;
    private DefaultListModel<String> onlineUsersModel;
    private JLabel lastLoginLabel;
    private ServerSocket fileReceiverSocket;
    private final File defaultSaveDirectory = new File("C:\\"); // يمكنك تغييره لاحقًا
    private Timer inactivityTimer;
    private Client client;
    private JButton broadcastButton;
    private JComboBox<String> statusCombo;

    public ClientGUI() {
        initializeGUI();
        this.client = new Client(this);
    }

    private void initializeGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.setMinimumSize(new Dimension(1000, 700));

        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(SECONDARY_COLOR);

        // 1. Top Panel - Login and Server Info
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 2. Center Panel - Chat and Online Users
        JPanel centerPanel = createCenterPanel();
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 3. Bottom Panel - Message Input and Network Info
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);

        updateNetworkInterfaces();
        addEventListeners();
        initializeInactivityTimer();
        setConnectedState(false);
    }

    @Override
    public void appendStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(message + "\n");
        });
    }

    @Override
    public void appendChatMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }


    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                new EmptyBorder(10, 10, 10, 10)
        ));
        topPanel.setBackground(Color.WHITE);
        JPanel loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel loginLabel = new JLabel("Login Details");
        loginLabel.setFont(new Font("Arial", Font.BOLD, 14));
        loginLabel.setForeground(PRIMARY_COLOR);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        loginPanel.add(loginLabel, gbc);

        gbc.gridwidth = 1; gbc.gridy = 1;
        loginPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        usernameField = new JTextField(20);
        decorateTextField(usernameField);
        loginPanel.add(usernameField, gbc);

        gbc.gridx = 2;
        loginPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 3;
        passwordField = new JPasswordField(20);
        decorateTextField(passwordField);
        loginPanel.add(passwordField, gbc);

        gbc.gridx = 4;
        loginButton = createStyledButton("Login", PRIMARY_COLOR, 100, 30);
        loginPanel.add(loginButton, gbc);

        gbc.gridx = 5;
        logoutButton = createStyledButton("Logout", ERROR_COLOR, 100, 30);
        logoutButton.setEnabled(false);
        loginPanel.add(logoutButton, gbc);

        gbc.gridx = 6;
        lastLoginLabel = new JLabel("Last login: Never");
        lastLoginLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        lastLoginLabel.setForeground(Color.GRAY);
        loginPanel.add(lastLoginLabel, gbc);

        // Server Info Panel
        JPanel serverPanel = new JPanel(new GridBagLayout());
        serverPanel.setBackground(Color.WHITE);

        GridBagConstraints gbcServer = new GridBagConstraints();
        gbcServer.insets = new Insets(5, 5, 5, 10);
        gbcServer.anchor = GridBagConstraints.WEST;

        JLabel serverLabel = new JLabel("Server Connection");
        serverLabel.setFont(new Font("Arial", Font.BOLD, 14));
        serverLabel.setForeground(PRIMARY_COLOR);

        gbcServer.gridx = 0; gbcServer.gridy = 0; gbcServer.gridwidth = 4;
        serverPanel.add(serverLabel, gbcServer);

        gbcServer.gridwidth = 1; gbcServer.gridy = 1;
        serverPanel.add(new JLabel("IP:"), gbcServer);

        gbcServer.gridx = 1;
        tcpServerIpField = new JTextField("127.0.0.1", 15);
        decorateTextField(tcpServerIpField);
        serverPanel.add(tcpServerIpField, gbcServer);

        gbcServer.gridx = 2;
        serverPanel.add(new JLabel("Port:"), gbcServer);

        gbcServer.gridx = 3;
        tcpServerPortField = new JTextField("8888", 5);
        decorateTextField(tcpServerPortField);
        serverPanel.add(tcpServerPortField, gbcServer);


        JPanel statusPanel = new JPanel();
        statusPanel.setBackground(Color.WHITE);

        statusCombo = new JComboBox<>(new String[]{"Active", "Busy", "away"});
        statusCombo.setBackground(Color.WHITE);
        statusCombo.setFont(new Font("Arial", Font.PLAIN, 12));
        statusCombo.addActionListener(e -> {
            client.setStatus((String) statusCombo.getSelectedItem());
            resetInactivityTimer();
        });

        statusPanel.add(new JLabel("Status:"));
        statusPanel.add(statusCombo);

        // Add all to top panel
        JPanel leftTop = new JPanel(new BorderLayout(10, 10));
        leftTop.add(loginPanel, BorderLayout.NORTH);
        leftTop.add(serverPanel, BorderLayout.CENTER);
        leftTop.setBackground(Color.WHITE);

        topPanel.add(leftTop, BorderLayout.WEST);
        topPanel.add(statusPanel, BorderLayout.EAST);

        return topPanel;
    }

    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(SECONDARY_COLOR);

        // ======= Chat Panel (Full Height) =======
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(PRIMARY_COLOR, 1), "Chat Messages"),
                new EmptyBorder(10, 10, 10, 10)
        ));
        chatPanel.setBackground(Color.WHITE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 16));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);

        // ======= Right Panel: Online Users + Status Messages =======
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBackground(SECONDARY_COLOR);

        // Online Users
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(PRIMARY_COLOR, 1), "Online Users"),
                new EmptyBorder(10, 10, 10, 10)
        ));
        usersPanel.setBackground(Color.WHITE);

        onlineUsersModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(onlineUsersModel);
        onlineUsersList.setFont(new Font("Arial", Font.PLAIN, 12));
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.setCellRenderer(new UserListRenderer());

        JScrollPane usersScrollPane = new JScrollPane(onlineUsersList);
        usersPanel.add(usersScrollPane, BorderLayout.CENTER);

        // Status Panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(PRIMARY_COLOR, 1), "Status Messages"),
                new EmptyBorder(10, 10, 10, 10)
        ));
        statusPanel.setBackground(Color.WHITE);

        statusArea = new JTextArea(6, 1);
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Arial", Font.PLAIN, 12));
        statusArea.setForeground(Color.DARK_GRAY);
        statusArea.setMargin(new Insets(5, 5, 5, 5));

        JScrollPane statusScrollPane = new JScrollPane(statusArea);
        statusPanel.add(statusScrollPane, BorderLayout.CENTER);

        // Add both to right panel
        rightPanel.add(usersPanel, BorderLayout.CENTER);
        rightPanel.add(statusPanel, BorderLayout.SOUTH);

        // Add to center panel
        centerPanel.add(chatPanel, BorderLayout.CENTER);
        centerPanel.add(rightPanel, BorderLayout.EAST);

        return centerPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                new EmptyBorder(10, 10, 10, 10)
        ));
        bottomPanel.setBackground(SECONDARY_COLOR);

        // Network Settings Panel
        JPanel networkPanel = new JPanel(new GridBagLayout());
        networkPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(PRIMARY_COLOR, 1), "Network Settings"),
                new EmptyBorder(10, 10, 10, 10)
        ));
        networkPanel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1: Network Interface
        gbc.gridx = 0; gbc.gridy = 0;
        networkPanel.add(new JLabel("Network Interface:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        interfaceComboBox = new JComboBox<>();
        interfaceComboBox.setPreferredSize(new Dimension(300, 30));
        interfaceComboBox.setBackground(Color.WHITE);
        networkPanel.add(interfaceComboBox, gbc);

        // Row 2: Local IP and Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0;
        networkPanel.add(new JLabel("Local IP:"), gbc);

        gbc.gridx = 1;
        localIpField = new JTextField(15);
        localIpField.setEditable(true);
        decorateTextField(localIpField);
        networkPanel.add(localIpField, gbc);

        gbc.gridx = 2;
        networkPanel.add(new JLabel("Local Port:"), gbc);

        gbc.gridx = 3;
        localPortField = new JTextField("5000", 8);
        decorateTextField(localPortField);
        networkPanel.add(localPortField, gbc);

        // Row 3: Remote IP and Port
        gbc.gridx = 0; gbc.gridy = 2;
        networkPanel.add(new JLabel("Remote IP:"), gbc);

        gbc.gridx = 1;
        remoteIpField = new JTextField(15);
        decorateTextField(remoteIpField);
        networkPanel.add(remoteIpField, gbc);

        gbc.gridx = 2;
        networkPanel.add(new JLabel("Remote Port:"), gbc);

        gbc.gridx = 3;
        remotePortField = new JTextField("6000", 8);
        decorateTextField(remotePortField);
        networkPanel.add(remotePortField, gbc);

        // Row 4: Test Connection Button
        gbc.gridx = 4; gbc.gridy = 1; gbc.gridheight = 2; gbc.fill = GridBagConstraints.VERTICAL;
        testButton = createStyledButton("Test Connection", ACCENT_COLOR, 150, 60);
        networkPanel.add(testButton, gbc);

        // Message Input Panel
        JPanel messagePanel = new JPanel(new BorderLayout(10, 10));
        messagePanel.setBackground(Color.WHITE);

        messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));
        messageField.setBorder(new CompoundBorder(
                new LineBorder(Color.LIGHT_GRAY),
                new EmptyBorder(10, 15, 10, 15)
        ));
        messageField.setEnabled(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonPanel.setBackground(Color.WHITE);

        sendButton = createStyledButton("Send", PRIMARY_COLOR, 100, 35);
        sendButton.setEnabled(false);
        clearButton = createStyledButton("Clear", new Color(150, 150, 150), 100, 35);
        archiveButton = createStyledButton("Archive", new Color(100, 149, 237), 100, 35);
        exportButton = createStyledButton("Export", SUCCESS_COLOR, 100, 35);
        JButton sendFileButton = createStyledButton("Send File", PRIMARY_COLOR, 100, 35);
        sendFileButton.addActionListener(e -> sendFile());
        buttonPanel.add(sendFileButton);
        broadcastButton = createStyledButton("send to all", new Color(100, 149, 237), 100, 35);
        broadcastButton.addActionListener(e -> broadcastToAll());
        buttonPanel.add(broadcastButton);  // أضف هذا السطر قبل sendButton
        buttonPanel.add(exportButton);
        buttonPanel.add(archiveButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(sendButton);

        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(buttonPanel, BorderLayout.EAST);

        bottomPanel.add(networkPanel, BorderLayout.CENTER);
        bottomPanel.add(messagePanel, BorderLayout.SOUTH);

        return bottomPanel;
    }



    private JButton createStyledButton(String text, Color bgColor, int width, int height) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.black);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setPreferredSize(new Dimension(width, height));
        button.setBorder(new CompoundBorder(
                new LineBorder(bgColor.darker(), 1),
                new EmptyBorder(5, 15, 5, 15)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                button.setBackground(bgColor.brighter());
                button.setBorder(new CompoundBorder(
                        new LineBorder(bgColor.brighter().darker(), 1),
                        new EmptyBorder(5, 15, 5, 15)
                ));
            }

            public void mouseExited(MouseEvent evt) {
                button.setBackground(bgColor);
                button.setBorder(new CompoundBorder(
                        new LineBorder(bgColor.darker(), 1),
                        new EmptyBorder(5, 15, 5, 15)
                ));
            }
        });

        return button;
    }

    private void decorateTextField(JTextField field) {
        field.setFont(new Font("Arial", Font.PLAIN, 13));
        field.setBorder(new CompoundBorder(
                new LineBorder(Color.LIGHT_GRAY),
                new EmptyBorder(8, 12, 8, 12)
        ));
        field.setMinimumSize(new Dimension(200, 35));
        field.setPreferredSize(new Dimension(250, 35));
    }

    private void updateNetworkInterfaces() {
        interfaceComboBox.removeAllItems();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address) {
                            interfaceComboBox.addItem(ni.getDisplayName() + ": " + addr.getHostAddress());
                        }
                    }
                }
            }
        } catch (SocketException e) {
            appendStatus("Error getting network interfaces: " + e.getMessage());
        }

        interfaceComboBox.addActionListener(e -> {
            String selected = (String) interfaceComboBox.getSelectedItem();
            if (selected != null) {
                String ip = selected.split(": ")[1];
                localIpField.setText(ip);
            }
        });
    }

    private void addEventListeners() {
        loginButton.addActionListener(e -> login());
        logoutButton.addActionListener(e -> logout());
        sendButton.addActionListener(e -> sendMessage());
        clearButton.addActionListener(e -> clearChat());
        archiveButton.addActionListener(e -> showArchive());
        exportButton.addActionListener(e -> exportChat());
        testButton.addActionListener(e -> testConnection());

        messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendMessage();
                resetInactivityTimer();
            }
        });

        frame.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { resetInactivityTimer(); }
        });

        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { resetInactivityTimer(); }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logout();
            }
        });
    }

    private javax.swing.Timer archiveTimer; // Class field

    private void clearChat() {
        System.out.println("clearChat() called at " + new Date()); // Debug

        // 1. Archive current chat
        String currentChat = chatArea.getText();
        System.out.println("Archiving chat: " + (currentChat.isEmpty() ? "EMPTY" : "HAS CONTENT"));

        if (!currentChat.isEmpty()) {
            client.setArchive(currentChat);
            System.out.println("Archive saved at " + new Date());
        }

        // 2. Clear chat area
        chatArea.setText("");
        appendStatus("Chat history cleared. Archive will auto-clear in 30 seconds.");
        System.out.println("Chat UI cleared at " + new Date());

        // 3. Setup timer
        if (archiveTimer == null) {
            System.out.println("Creating new timer instance");
            archiveTimer = new javax.swing.Timer(30000, e -> {
                client.clearArchive(); // Use the dedicated clear method
                appendStatus("Archive cleared after 30 seconds");
                clearArchive();
            });
            archiveTimer.setRepeats(false);
        }

        System.out.println("(Re)starting timer at " + new Date());
        archiveTimer.restart(); // This both stops any existing and starts new
    }

    private void clearArchive() {
        System.out.println("clearArchive() called at " + new Date());

        // 1. Verify and clear
        String currentArchive = client.getArchive();
        System.out.println("Current archive: " + (currentArchive == null ? "NULL" :
                currentArchive.isEmpty() ? "EMPTY" : "HAS CONTENT"));

        if (currentArchive != null && !currentArchive.isEmpty()) {
            client.setArchive("");
            System.out.println("Archive cleared at " + new Date());
        }

        // 2. Update UI
        appendStatus("Archive automatically cleared after 30 seconds");
        System.out.println("Status updated at " + new Date());

        // 3. Cleanup timer
        if (archiveTimer != null) {
            archiveTimer.stop();
            System.out.println("Timer stopped at " + new Date());
        }
    }
    private void initializeInactivityTimer() {
        inactivityTimer = new Timer(30000, e -> {
            if (!client.getStatus().equals("Away")&& !client.getStatus().equals("away")) {
                client.setStatus("Away");
                appendStatus("Status changed to Away due to inactivity");
            }
        });
        inactivityTimer.setRepeats(true);
    }

    private void resetInactivityTimer() {
        inactivityTimer.restart();

        String selectedStatus = Objects.requireNonNull(statusCombo.getSelectedItem()).toString();

        if (selectedStatus.equals("away")) {
            return;
        }

        if (client.getStatus().equals("Away")) {
            client.setStatus("Active");
            statusCombo.setSelectedItem("Active");
        }
    }

    private void setConnectedState(boolean connected) {
        loginButton.setEnabled(!connected);
        logoutButton.setEnabled(connected);
        usernameField.setEnabled(!connected);
        passwordField.setEnabled(!connected);
        tcpServerIpField.setEnabled(!connected);
        tcpServerPortField.setEnabled(!connected);
        sendButton.setEnabled(connected);
        messageField.setEnabled(connected);

        if (!connected) {
            onlineUsersModel.clear();
        }
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        String serverIp = tcpServerIpField.getText().trim();
        String serverPortStr = tcpServerPortField.getText().trim();
        String localPortStr = localPortField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            appendStatus("Username and password are required");
            return;
        }

        if (serverIp.isEmpty() || serverPortStr.isEmpty() || localPortStr.isEmpty()) {
            appendStatus("Server IP, server port, and local port are required");
            return;
        }

        // ضبط IP تلقائي من القائمة إذا كان فارغ
        if (localIpField.getText().trim().isEmpty() && interfaceComboBox.getItemCount() > 0) {
            String selected = (String) interfaceComboBox.getItemAt(0);
            String ip = selected.split(": ")[1];
            localIpField.setText(ip);
        }

        try {
            int serverPort = Integer.parseInt(serverPortStr);
            int localPort = Integer.parseInt(localPortStr);
            String localIp = localIpField.getText().trim();

            client.login(username, password, serverIp, serverPort, localIp, localPort);

            lastLoginLabel.setText("Last login: " + client.getLastLoginTime());
            setConnectedState(true);
            appendStatus("Logged in successfully at " + client.getLastLoginTime());
            inactivityTimer.start();

            // ⚡ تشغيل مستقبل الملفات بعد تسجيل الدخول
            try {
                fileReceiverSocket = new ServerSocket(0); // 0 = أي منفذ متاح
                FileTransferManager.receiveFile(fileReceiverSocket, defaultSaveDirectory, this);
                appendStatus("Listening for file transfers on port: " + fileReceiverSocket.getLocalPort());
            } catch (IOException e) {
                appendStatus("Failed to start file receiver: " + e.getMessage());
            }

        } catch (NumberFormatException e) {
            appendStatus("Invalid port number");
        } catch (IOException e) {
            appendStatus("Error connecting: " + e.getMessage());
        }
    }


    private void logout() {
        try {
            client.logout(); // يُرسل "LOGOUT" للسيرفر
            setConnectedState(false);
            appendStatus("Logged out");
            inactivityTimer.stop();
            onlineUsersModel.clear(); // يمسح القائمة محليًا
        } catch (Exception e) {
            appendStatus("Error during logout: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        String remoteIp = remoteIpField.getText().trim();
        String remotePortStr = remotePortField.getText().trim();

        if (remoteIp.isEmpty() || remotePortStr.isEmpty()) {
            appendStatus("Remote IP and port are required");
            return;
        }

        try {
            int remotePort = Integer.parseInt(remotePortStr);
            client.sendMessage(message, remoteIp, remotePort);
            messageField.setText("");
            resetInactivityTimer();
        } catch (NumberFormatException e) {
            appendStatus("Invalid remote port number");
        } catch (IOException e) {
            appendStatus("Error sending message: " + e.getMessage());
        }
    }

    private void testConnection() {
        String remoteIp = remoteIpField.getText().trim();
        String remotePortStr = remotePortField.getText().trim();

        if (remoteIp.isEmpty() || remotePortStr.isEmpty()) {
            appendStatus("Remote IP and port are required for testing");
            return;
        }

        try {
            int remotePort = Integer.parseInt(remotePortStr);
            appendStatus("Testing connection to " + remoteIp + ":" + remotePort + "...");
            client.testConnection(remoteIp, remotePort);
            appendStatus("Test message sent successfully");
        } catch (NumberFormatException e) {
            appendStatus("Invalid remote port number");
        } catch (IOException e) {
            appendStatus("Error sending test message: " + e.getMessage());
        }
    }



    private void showArchive() {
        JFrame archiveFrame = new JFrame("Archived Messages");
        archiveFrame.setSize(400, 300);

        JTextArea archiveArea = new JTextArea();
        archiveArea.setEditable(false);

        for (String msg : client.getArchivedMessages()) {
            archiveArea.append(msg+ "\n");
        }

        archiveFrame.add(new JScrollPane(archiveArea));
        archiveFrame.setVisible(true);
    }

    private void exportChat() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Chat History");
        fileChooser.setSelectedFile(new File("chat_history.txt"));

        if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(fileToSave)) {
                writer.write(chatArea.getText());
                appendStatus("Chat history exported to: " + fileToSave.getAbsolutePath());
            } catch (IOException e) {
                appendStatus("Error exporting chat history: " + e.getMessage());
            }
        }
    }


    public void updateOnlineUsersList(String clientListStr) {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.clear();
            String[] clients = clientListStr.split(",");
            for (String client : clients) {
                if (!client.isEmpty()) onlineUsersModel.addElement(client);
            }
        });
    }

    class UserListRenderer extends DefaultListCellRenderer {
        private Color[] userColors = {
                new Color(70, 130, 180),   // Active
                new Color(205, 92, 92),    // Busy
                new Color(218, 165, 32)    // Away
        };

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value != null) {
                String val = value.toString();
                if (val.contains("Active")) setForeground(userColors[0]);
                else if (val.contains("Busy")) setForeground(userColors[1]);
                else if (val.contains("Away")||val.contains("away")) setForeground(userColors[2]);

                String[] parts = val.split(" - ");
                if (parts.length > 0) {
                    setText("<html><b>" + parts[0] + "</b> - " + (parts.length > 1 ? parts[1] : "") + "</html>");
                }
            }

            if (isSelected) setBackground(new Color(220, 240, 255));
            else setBackground(Color.WHITE);

            return this;
        }
    }

    // في ClientGUI.java


    private void sendFile() {
        if (!client.isLoggedIn()) {
            appendStatus("يجب تسجيل الدخول أولاً");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String receiverIp = remoteIpField.getText().trim();
            String receiverPortStr = remotePortField.getText().trim();

            if (receiverIp.isEmpty() || receiverPortStr.isEmpty()) {
                appendStatus("يجب تحديد IP و port المستلم");
                return;
            }

            try {
                // Use port+1 for file transfers (as defined in Server.java)
                int receiverPort = Integer.parseInt(receiverPortStr) + 1;
                FileTransferManager.sendFile(selectedFile, receiverIp, receiverPort, this);
                appendStatus("جارٍ إرسال الملف: " + selectedFile.getName());
            } catch (NumberFormatException e) {
                appendStatus("رقم المنفذ غير صحيح");
            } catch (IOException e) {
                appendStatus("خطأ في الإرسال: " + e.getMessage());
                e.printStackTrace(); // Add this for debugging
            }
        }
    }

    private void broadcastToAll() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        try {
            // إنشاء خريطة المستخدمين
            Map<String, String> onlineUsers = new HashMap<>();
            for (int i = 0; i < onlineUsersModel.size(); i++) {
                String user = onlineUsersModel.getElementAt(i);
                String[] parts = user.split(" - ");
                if (parts.length == 2) {
                    String username = parts[0].trim();
                    String addressPart = parts[1].split("\\|")[0].trim();
                    onlineUsers.put(username, addressPart);
                }
            }

            client.broadcastMessage(message, onlineUsers);
            messageField.setText("");
            resetInactivityTimer();
        } catch (IOException e) {
            appendStatus("Error broadcasting message: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}