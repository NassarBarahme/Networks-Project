package proj;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;

public class PeerToPeerChat extends JFrame {
    private JTextField messageField, localIPField, localPortField, remoteIPField, remotePortField;
    private JButton sendButton, deleteAllButton, clearFieldButton, deleteSelectedButton, archiveButton, restoreButton, connectButton;
    private DefaultListModel<String> listModel;
    private JList<String> messageList;
    private ArrayList<String> archivedMessages;
    private DatagramSocket socket;
    private InetAddress remoteAddress;
    private int remotePort;
    private boolean running;
    private Timer archiveTimer;
    private boolean socketInitialized = false;
    private boolean peerReady = false;
    private final File logFile = new File("C:\\Users\\s1221\\OneDrive\\Desktop\\NetProj (2)\\chatt_log.txt");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

    public PeerToPeerChat() {
        this.archivedMessages = new ArrayList<>();
        this.running = true;
        this.archiveTimer = new Timer();

        setTitle("P2P Chat");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(240, 240, 245));

        add(connectionPanel(), BorderLayout.NORTH);
        add(chatPanel(), BorderLayout.CENTER);
        add(inputPanel(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running = false;
                archiveTimer.cancel();
                if (socket != null && !socket.isClosed()) socket.close();
            }
        });
    }

    private void logEvent(String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(timeFormat.format(new Date()) + " - " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeSocket() {
        try {
            String localIP = localIPField.getText();
            int localPort = Integer.parseInt(localPortField.getText());
            if (socket != null && !socket.isClosed()) socket.close();
            socket = new DatagramSocket(localPort, InetAddress.getByName(localIP));
            new Thread(this::listenForMessages).start();
            socketInitialized = true;

            new Timer().schedule(new TimerTask() {
                public void run() {
                    sendUDPMessage("__ping__");
                }
            }, 500);

            Thread.sleep(500);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            socketInitialized = false;
        }
    }
    private JPanel connectionPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 4, 10, 10));
        panel.setBackground(new Color(220, 230, 240));
        panel.setBorder(BorderFactory.createTitledBorder("Connection"));

        panel.add(new JLabel("Local IP:"));
        localIPField = new JTextField("127.0.0.1");
        panel.add(localIPField);
        panel.add(new JLabel("Local Port:"));
        localPortField = new JTextField("6000");
        panel.add(localPortField);

        panel.add(new JLabel("Remote IP:"));
        remoteIPField = new JTextField("127.0.0.1");
        panel.add(remoteIPField);
        panel.add(new JLabel("Remote Port:"));
        remotePortField = new JTextField("5000");
        panel.add(remotePortField);

        panel.add(new JLabel(""));
//        connectButton = new JButton("Connect");
//        connectButton.setBackground(new Color(100, 100, 200));
        connectButton = new JButton("Test");
        connectButton.setBackground(new Color(70, 130, 180));
        connectButton.setPreferredSize(new Dimension(100, 30));

        connectButton.setForeground(Color.WHITE);
        connectButton.addActionListener(e -> initializeSocket());
        panel.add(connectButton);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        return panel;
    }

    private JPanel chatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(250, 252, 255));
        panel.setBorder(BorderFactory.createTitledBorder("Messages"));

        listModel = new DefaultListModel<>();
        messageList = new JList<>(listModel);
        messageList.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        messageList.setFixedCellHeight(40);
        messageList.setCellRenderer(new ColorfulMessageRenderer());

        panel.add(new JScrollPane(messageList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel inputPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(230, 235, 240));

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        messageField = new JTextField();
        topPanel.add(messageField, BorderLayout.CENTER);

        sendButton = new JButton("SEND");
        sendButton.setBackground(new Color(100, 180, 100));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        topPanel.add(sendButton, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        deleteAllButton = new JButton("Delete All");
        deleteAllButton.setBackground(new Color(220, 120, 120));
        deleteAllButton.setForeground(Color.WHITE);
        bottomPanel.add(deleteAllButton);

        clearFieldButton = new JButton("Clear Field");
        clearFieldButton.setBackground(new Color(180, 180, 100));
        clearFieldButton.setForeground(Color.WHITE);
        bottomPanel.add(clearFieldButton);

        deleteSelectedButton = new JButton("Delete Selected");
        deleteSelectedButton.setBackground(new Color(200, 120, 150));
        deleteSelectedButton.setForeground(Color.WHITE);
        bottomPanel.add(deleteSelectedButton);

        archiveButton = new JButton("Show Archive");
        archiveButton.setBackground(new Color(100, 150, 200));
        archiveButton.setForeground(Color.WHITE);
        bottomPanel.add(archiveButton);

        restoreButton = new JButton("Restore");
        restoreButton.setBackground(new Color(120, 180, 120));
        restoreButton.setForeground(Color.WHITE);
        bottomPanel.add(restoreButton);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        deleteAllButton.addActionListener(e -> deleteAllMessages());
        clearFieldButton.addActionListener(e -> messageField.setText(""));
        deleteSelectedButton.addActionListener(e -> deleteSelectedMessage());
        archiveButton.addActionListener(e -> showArchiveDialog());
        restoreButton.addActionListener(e -> restoreLastArchived());
        return panel;
    }
    private void sendMessage() {
        String text = messageField.getText().trim();
        if (!text.isEmpty()) {
            String timestamp = timeFormat.format(new Date());
            SwingUtilities.invokeLater(() -> listModel.addElement("[Sent]: [" + timestamp + "] " + text));
            messageField.setText("");
            logEvent("Sent: " + text);

            try {
                String remoteIP = remoteIPField.getText();
                this.remotePort = Integer.parseInt(remotePortField.getText());
                this.remoteAddress = InetAddress.getByName(remoteIP);

                if (!socketInitialized || socket == null || socket.isClosed()) {
                    initializeSocket();
                }
                new Thread(() -> sendUDPMessage(text)).start();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private void sendUDPMessage(String message) {
        try {
            DatagramPacket sendPacket = new DatagramPacket(message.getBytes(), message.length(), remoteAddress, remotePort);
            socket.send(sendPacket);
        } catch (Exception e) {
            System.err.println("Failed to send: " + e.getMessage());
        }
    }

    private void listenForMessages() {
        try {
            byte[] receiveData = new byte[1024];
            while (running) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

                if (receivedMessage.trim().equals("__ping__")) {
                    sendUDPMessage("__pong__");
                    continue;
                }
                if (receivedMessage.trim().equals("__pong__")) {
                    peerReady = true;
                    System.out.println("Peer is ready.");
                    continue;
                }

                String timestamp = timeFormat.format(new Date());
                SwingUtilities.invokeLater(() -> listModel.addElement("[Received]: [" + timestamp + "] " + receivedMessage));
                logEvent("Received: " + receivedMessage);
            }
        } catch (IOException e) {
            if (running) System.err.println("Socket error: " + e.getMessage());
        }
    }

    private void deleteSelectedMessage() {
        int selectedIndex = messageList.getSelectedIndex();
        if (selectedIndex != -1) {
            String message = listModel.getElementAt(selectedIndex);
            archivedMessages.add(message);
            logEvent("Archived: " + message);
            listModel.remove(selectedIndex);
            startArchiveTimer();
        }
    }

    private void deleteAllMessages() {
        for (int i = 0; i < listModel.size(); i++) {
            String msg = listModel.get(i);
            archivedMessages.add(msg);
            logEvent("Archived: " + msg);
        }
        listModel.clear();
        startArchiveTimer();
    }

    private void startArchiveTimer() {
        archiveTimer.cancel();
        archiveTimer = new Timer();
        archiveTimer.schedule(new TimerTask() {
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    archivedMessages.clear();
                    JOptionPane.showMessageDialog(PeerToPeerChat.this, "Archive cleared after 2 minutes.", "Auto Clear", JOptionPane.INFORMATION_MESSAGE);
                    logEvent("Archive auto-cleared");
                });
            }
        }, 120000);
    }

    private void showArchiveDialog() {
        JDialog archiveDialog = new JDialog(this, "Archived Messages", true);
        archiveDialog.setSize(500, 400);
        archiveDialog.setLocationRelativeTo(this);

        DefaultListModel<String> archiveModel = new DefaultListModel<>();
        for (String msg : archivedMessages) archiveModel.addElement(msg);

        JList<String> archiveList = new JList<>(archiveModel);
        archiveDialog.add(new JScrollPane(archiveList));
        archiveDialog.setVisible(true);
    }

    private void restoreLastArchived() {
        if (!archivedMessages.isEmpty()) {
            String msg = archivedMessages.remove(archivedMessages.size() - 1);
            listModel.addElement(msg);
            logEvent("Restored: " + msg);
        }
    }

    private static class ColorfulMessageRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String message = value.toString();
            if (message.startsWith("[Sent]")) label.setBackground(Color.YELLOW);
            else if (message.startsWith("[Received]")) label.setBackground(Color.ORANGE);
            else label.setBackground(Color.WHITE);
            return label;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PeerToPeerChat().setVisible(true));
    }
}
