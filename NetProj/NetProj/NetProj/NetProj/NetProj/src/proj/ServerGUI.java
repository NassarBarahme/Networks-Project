package proj;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;

public class ServerGUI implements FileTransferListener  {
    private static final String LOG_DIRECTORY = "server_logs";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat LOG_FILE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private JFrame frame;
    private JTextArea logArea;
    private JList<String> clientList;
    private DefaultListModel<String> listModel;
    private JTextField portField;
    private JButton startButton;
    private JLabel serverStatusLabel;
    private JLabel connectedClientsLabel;
    private Timer logSaveTimer;

    private Server server;



    public ServerGUI() {
        initializeGUI();
        server = new Server(this);
        setupLogAutoSave();
    }

    @Override
    public void appendStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void appendChatMessage(String message) {
        log(message);
    }

    private void setupLogAutoSave() {
        File logDir = new File(LOG_DIRECTORY);
        if (!logDir.exists()) {
            logDir.mkdir();
        }

        logSaveTimer = new Timer(true);
        logSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                saveLogToFile(false);
            }
        }, 60_000, 60_000);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saveLogToFile(false);
            if (logSaveTimer != null) {
                logSaveTimer.cancel();
            }
        }));
    }

    private void initializeGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        frame = new JFrame("proj.Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setMinimumSize(new Dimension(800, 600));

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(245, 245, 245));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(70, 130, 180));
        headerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel("proj.Server");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);

        serverStatusLabel = new JLabel("Status: Stopped");
        serverStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        serverStatusLabel.setForeground(new Color(220, 220, 220));

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(serverStatusLabel, BorderLayout.EAST);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        controlPanel.setBackground(Color.WHITE);
        controlPanel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                new EmptyBorder(10, 15, 10, 15)
        ));

        JLabel portLabel = new JLabel("Server Port:");
        portLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        portField = new JTextField(String.valueOf(Server.DEFAULT_PORT), 8);
        styleTextField(portField);

        startButton = new JButton("Start Server");
        styleButton(startButton, new Color(70, 130, 180), new Color(50, 110, 160));

        startButton.addActionListener(e -> {
            if (server.isRunning()) {
                server.stop();
                startButton.setText("Start Server");
                styleButton(startButton, new Color(70, 130, 180), new Color(50, 110, 160));
                serverStatusLabel.setText("Status: Stopped");
            } else {
                int port = Integer.parseInt(portField.getText());
                server.start(port);
                startButton.setText("Stop Server");
                styleButton(startButton, new Color(220, 80, 80), new Color(200, 60, 60));
                serverStatusLabel.setText("Status: Running on port " + port);
            }
        });

        controlPanel.add(portLabel);
        controlPanel.add(portField);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(startButton);

        // proj.Main Content Panel
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBackground(new Color(245, 245, 245));

        // Left Panel (proj.Client List and User Management)
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBackground(new Color(245, 245, 245));

        // proj.Client List Panel
        JPanel clientPanel = createTitledPanel("Connected Clients", new Color(70, 130, 180));

        listModel = new DefaultListModel<>();
        clientList = new JList<>(listModel);
        clientList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clientList.setCellRenderer(new ClientListRenderer());
        clientList.setFixedCellHeight(30);

        connectedClientsLabel = new JLabel("0 clients connected");
        connectedClientsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        connectedClientsLabel.setForeground(new Color(100, 100, 100));
        connectedClientsLabel.setBorder(new EmptyBorder(0, 5, 5, 5));

        JScrollPane listScrollPane = new JScrollPane(clientList);
        listScrollPane.setBorder(null);

        clientPanel.add(listScrollPane, BorderLayout.CENTER);
        clientPanel.add(connectedClientsLabel, BorderLayout.SOUTH);

        // User Management Panel
        JPanel userPanel = createTitledPanel("User Management", new Color(60, 179, 113));

        JPanel addUserPanel = new JPanel(new GridBagLayout());
        addUserPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel usernameLabel = new JLabel("Username:");
        usernameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        addUserPanel.add(usernameLabel, gbc);

        JTextField usernameField = new JTextField(15);
        styleTextField(usernameField);
        gbc.gridx = 1;
        gbc.gridy = 0;
        addUserPanel.add(usernameField, gbc);

        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 1;
        addUserPanel.add(passwordLabel, gbc);

        JPasswordField passwordField = new JPasswordField(15);
        styleTextField(passwordField);
        gbc.gridx = 1;
        gbc.gridy = 1;
        addUserPanel.add(passwordField, gbc);

        JButton addUserButton = new JButton("Add User");
        styleButton(addUserButton, new Color(60, 179, 113), new Color(50, 159, 103));
        addUserButton.setForeground(Color.black);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        addUserPanel.add(addUserButton, gbc);

        JButton removeUserButton = new JButton("Remove Selected");
        styleButton(removeUserButton, new Color(205, 92, 92), new Color(185, 72, 72));
        removeUserButton.setForeground(Color.black);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        addUserPanel.add(removeUserButton, gbc);

        addUserButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            if (username.isEmpty() || password.isEmpty()) {
                showMessage("Username and password cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            boolean f = server.addUser(username, password);
            if(!f){
                JOptionPane.showMessageDialog(frame, "User already exists", "Error", JOptionPane.ERROR_MESSAGE);
                return;

            }
            usernameField.setText("");
            passwordField.setText("");
            showMessage("User added successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
        });

        removeUserButton.addActionListener(e -> {
            String selected = clientList.getSelectedValue();
            if (selected != null) {
                String username = selected.split(" - ")[0].trim();
                int confirm = JOptionPane.showConfirmDialog(frame,
                        "Are you sure you want to remove user '" + username + "'?",
                        "Confirm Removal", JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    server.removeUser(username);
                    showMessage("User removed successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                showMessage("Please select a user to remove", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        userPanel.add(addUserPanel, BorderLayout.NORTH);

        leftPanel.add(clientPanel, BorderLayout.CENTER);
        leftPanel.add(userPanel, BorderLayout.SOUTH);

        // Log Panel
        JPanel logPanel = createTitledPanel("proj.Server Log", new Color(100, 100, 100));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(250, 250, 250));
        logArea.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(null);

        // Log controls
        JPanel logControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        logControlPanel.setBackground(Color.WHITE);

        JButton clearLogButton = new JButton("Clear Log");
        styleButton(clearLogButton, new Color(100, 100, 100), new Color(80, 80, 80));
        clearLogButton.setForeground(Color.black);
        clearLogButton.addActionListener(e -> logArea.setText(""));

        logControlPanel.add(clearLogButton);

        logPanel.add(logScrollPane, BorderLayout.CENTER);
        logPanel.add(logControlPanel, BorderLayout.SOUTH);

        contentPanel.add(leftPanel, BorderLayout.WEST);
        contentPanel.add(logPanel, BorderLayout.CENTER);

        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(controlPanel, BorderLayout.CENTER);
        mainPanel.add(contentPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(DATE_FORMAT.format(new Date()) + " - " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateClientList(Set<String> clients) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (String client : clients) {
                listModel.addElement(client);
            }
            connectedClientsLabel.setText(clients.size() + " client(s) connected");
        });
    }

    private void saveLogToFile(boolean showMessage) {
        String timestamp = LOG_FILE_FORMAT.format(new Date());
        File logFile = new File(LOG_DIRECTORY, "server_log_" + timestamp + ".txt");

        try (PrintWriter writer = new PrintWriter(logFile)) {
            writer.write(logArea.getText());
            if (showMessage) {
                SwingUtilities.invokeLater(() -> {
                    showMessage("Log saved successfully to " + logFile.getName(),
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                });
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                log("Error saving log file: " + e.getMessage());
                if (showMessage) {
                    showMessage("Error saving log file", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }
    }

    private void showMessage(String message, String title, int messageType) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(frame, message, title, messageType);
        });
    }

    private JPanel createTitledPanel(String title, Color titleColor) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        TitledBorder border = new TitledBorder(
                new LineBorder(new Color(220, 220, 220)),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                titleColor
        );

        border.setTitleJustification(TitledBorder.LEFT);
        panel.setBorder(new CompoundBorder(
                border,
                new EmptyBorder(5, 5, 5, 5)
        ));

        return panel;
    }

    private void styleButton(JButton button, Color bgColor, Color hoverColor) {
        button.setBackground(bgColor);
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(5, 15, 5, 15));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                button.setBackground(hoverColor);
            }

            public void mouseExited(MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });
    }

    private void styleTextField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        field.setBorder(new CompoundBorder(
                new LineBorder(new Color(200, 200, 200)),
                new EmptyBorder(5, 8, 5, 8)
        ));
    }

    class ClientListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value != null) {
                String val = value.toString();
                if (val.contains("Active")) {
                    setForeground(new Color(70, 130, 180));
                } else if (val.contains("Busy")) {
                    setForeground(new Color(205, 92, 92));
                } else if (val.contains("Away")||val.contains("away")) {
                    setForeground(new Color(218, 165, 32));
                }

                String[] parts = val.split(" - ");
                if (parts.length > 0) {
                    setText("<html><b>" + parts[0] + "</b> - " + (parts.length > 1 ? parts[1] : "") + "</html>");
                }
            }

            if (isSelected) {
                setBackground(new Color(220, 240, 255));
            } else {
                setBackground(Color.WHITE);
            }

            setBorder(new EmptyBorder(5, 5, 5, 5));

            return this;
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new ServerGUI();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Failed to start server: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}