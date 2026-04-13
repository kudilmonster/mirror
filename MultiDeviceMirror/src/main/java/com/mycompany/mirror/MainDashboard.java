package com.mycompany.mirror;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainDashboard extends JFrame {

    private int mouseX, mouseY;
    // ================= CUSTOM COMPONENTS =================
    // Class untuk membuat panel dengan sudut melengkung

    class RoundedPanel extends JPanel {

        private int cornerRadius = 15;

        public RoundedPanel(int radius) {
            super();
            this.cornerRadius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Dimension arcs = new Dimension(cornerRadius, cornerRadius);
            int width = getWidth();
            int height = getHeight();
            Graphics2D graphics = (Graphics2D) g;

            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(getBackground());
            graphics.fillRoundRect(0, 0, width - 1, height - 1, arcs.width, arcs.height);
        }
    }

    // ================= CONFIG & SERVICES =================
    private final String basePath = System.getProperty("user.dir") + "\\scrcpy\\";
    private final String scrcpyExecutable = basePath + "scrcpy.exe";
    public final String adbExecutable = basePath + "adb.exe";

    public ScrcpyService scrcpyService;
    private SyncService syncService;
    public AdbService adbService;

    // ================= STATE =================
    private final Set<String> lastDevices = new HashSet<>();
    private final Map<String, Integer> deviceSlotMap = new ConcurrentHashMap<>();
    public final ExecutorService executor = Executors.newFixedThreadPool(10);

    private RecordControlFrame recordControlFrame;
    public ViewerFrame viewerFrame;
    private javax.swing.JCheckBox chkAutoRecord;
    public RoundedPanel panelLayar;

    // ================= CONSTRUCTOR =================
    public MainDashboard() {
        initComponents();
        // 🔥 GARANSI INISIALISASI: Jika NetBeans ikut menghapus panel ini dari UI, kita buat ulang!
        if (panelLayar == null) {
            panelLayar = new RoundedPanel(25);
            panelLayar.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        }

        // 1. Inisialisasi layar Viewer yang terpisah
        viewerFrame = new ViewerFrame(panelLayar);

        // 2. Inisialisasi service
        initServices();

        // 3. Rapikan UI Dashboard
        initUI();
        startAutoDeviceWatcher();
    }

    private void initServices() {
        scrcpyService = new ScrcpyService(scrcpyExecutable, executor, this);
        adbService = new AdbService(this, adbExecutable, executor);

        // Sinkronisasi input diberikan ke viewerFrame
        syncService = new SyncService(this, viewerFrame, panelLayar, chkSync);
    }

    private void initUI() {
        // Bersihkan layout bawaan NetBeans
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());

        // Susun kembali panel
        getContentPane().add(panelTopBar, BorderLayout.NORTH);
        getContentPane().add(panelDevices, BorderLayout.CENTER);

        // Styling list device & log
        panelTopBar.setBackground(Color.BLACK);
        panelDevices.setBackground(Color.WHITE);
        panelDevices.setBorder(BorderFactory.createEmptyBorder(10, 2, 10, 2));
        setupLogStyle();

        // Dashboard dikecilkan karena layar sudah dipisah
        setSize(380, 700);
        getContentPane().revalidate();
        getContentPane().repaint();

        // Tampilkan layar kedua (Viewer)
        viewerFrame.setVisible(true);

        // ======== EVENT LISTENERS ========
        viewerFrame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                if (syncService != null) {
                    syncService.updatePosition();
                }
            }

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (syncService != null) {
                    syncService.updatePosition();
                }
            }
        });

        java.awt.event.WindowAdapter closeAdapter = new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                log("Mematikan semua sistem...");
                shutdown();
                System.exit(0);
            }
        };

        addWindowListener(closeAdapter);
        viewerFrame.addWindowListener(closeAdapter);
    }

    // ================= GETTERS & SETTERS =================
    public javax.swing.JList<String> getJListDevices() {
        return jListDevices;
    }

    public javax.swing.JSpinner getSpinX() {
        return spinX;
    }

    public javax.swing.JSpinner getSpinY() {
        return spinY;
    }

    public void setRecordingEnabled(javax.swing.JCheckBox chk) {
        this.chkAutoRecord = chk;
    }

    public String getSelectedWindowTitle() {
        String deviceId = jListDevices.getSelectedValue();
        if (deviceId == null) {
            return null;
        }

        for (Map.Entry<String, Integer> entry : deviceSlotMap.entrySet()) {
            if (entry.getKey().equals(deviceId)) {
                return "Mirror_" + entry.getValue() + "_ORIG";
            }
        }
        return null;
    }

    public java.awt.Canvas getCanvasFromSlot(String windowTitle) {
        try {
            String[] parts = windowTitle.split("_");
            if (parts.length < 2) {
                return null; // Keamanan array
            }
            int slotIndex = Integer.parseInt(parts[1]);
            if (slotIndex >= panelLayar.getComponentCount()) {
                return null; // Keamanan index
            }
            javax.swing.JPanel slotPanel = (javax.swing.JPanel) panelLayar.getComponent(slotIndex);
            for (java.awt.Component comp : slotPanel.getComponents()) {
                if (comp instanceof java.awt.Canvas) {
                    return (java.awt.Canvas) comp;
                }
            }
        } catch (Exception e) {
            log("Gagal menemukan Canvas untuk " + windowTitle);
        }
        return null;
    }

    private void shutdown() {
        scrcpyService.stopAll();
        executor.shutdownNow();
    }

    // ================= HELPER METHODS =================
    private int findAvailableSlotIndex() {
        // Cari angka index terkecil yang belum digunakan di deviceSlotMap (Maks 50 HP)
        for (int i = 0; i < 50; i++) {
            if (!deviceSlotMap.containsValue(i)) {
                return i;
            }
        }
        return panelLayar.getComponentCount(); // Fallback
    }

    // ================= DYNAMIC GRID =================
    private void syncGridPanels(int requiredCount) {
        int currentCount = panelLayar.getComponentCount();
        panelLayar.setLayout(new java.awt.GridLayout(0, 5, 10, 10));

        if (currentCount < requiredCount) {
            for (int i = currentCount; i < requiredCount; i++) {
                JPanel cell = new JPanel(new java.awt.BorderLayout());
                cell.setBackground(java.awt.Color.BLACK);
                cell.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(50, 50, 50)));

                // HEADER (Judul & Tombol Close)
                JPanel header = new JPanel(new java.awt.BorderLayout());
                header.setBackground(new java.awt.Color(30, 30, 30));
                header.setPreferredSize(new java.awt.Dimension(0, 25));

                JLabel lblTitle = new JLabel(" Slot Kosong " + (i + 1));
                lblTitle.setForeground(java.awt.Color.LIGHT_GRAY);
                lblTitle.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));

                JButton btnClose = new JButton("X");
                btnClose.setMargin(new java.awt.Insets(0, 5, 0, 5));
                btnClose.setBackground(new java.awt.Color(200, 50, 50));
                btnClose.setForeground(java.awt.Color.WHITE);
                btnClose.setFocusPainted(false);
                btnClose.setVisible(false);

                header.add(lblTitle, java.awt.BorderLayout.CENTER);
                header.add(btnClose, java.awt.BorderLayout.EAST);

                // KANVAS LAYAR
                java.awt.Canvas c = new java.awt.Canvas();
                c.setBackground(java.awt.Color.BLACK);

                cell.add(header, java.awt.BorderLayout.NORTH);
                cell.add(c, java.awt.BorderLayout.CENTER);

                panelLayar.add(cell);
            }
        }

        panelLayar.revalidate();
        panelLayar.repaint();
    }

    public void clearGrid() {
        SwingUtilities.invokeLater(() -> {
            panelLayar.removeAll();
            panelLayar.revalidate();
            panelLayar.repaint();
        });
    }

    // ================= CORE MIRROR =================
    private void startScrcpyMirror(String deviceId, boolean isClone) {
        if (deviceId == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            int targetIndex = findAvailableSlotIndex();

            // Format ID Bayangan untuk fitur Clone
            String slotKey = isClone ? deviceId + "_clone_" + System.currentTimeMillis() : deviceId;

            log("Membuka Slot " + targetIndex + " untuk: " + deviceId);
            deviceSlotMap.put(slotKey, targetIndex);

            // Pastikan Grid siap
            syncGridPanels(targetIndex + 1);

            // Akses komponen Cell
            JPanel cell = (JPanel) panelLayar.getComponent(targetIndex);
            java.awt.BorderLayout layout = (java.awt.BorderLayout) cell.getLayout();

            JPanel header = (JPanel) layout.getLayoutComponent(java.awt.BorderLayout.NORTH);
            java.awt.Canvas targetCanvas = (java.awt.Canvas) layout.getLayoutComponent(java.awt.BorderLayout.CENTER);

            java.awt.BorderLayout headerLayout = (java.awt.BorderLayout) header.getLayout();
            JLabel lblTitle = (JLabel) headerLayout.getLayoutComponent(java.awt.BorderLayout.CENTER);
            JButton btnClose = (JButton) headerLayout.getLayoutComponent(java.awt.BorderLayout.EAST);

            String windowTitle = "Mirror_" + targetIndex + "_" + (isClone ? "CLONE" : "ORIG");

            // Setup UI Header
            lblTitle.setText(" " + deviceId + (isClone ? " (Clone)" : ""));
            btnClose.setVisible(true);

            // Reset listener tombol close
            for (java.awt.event.ActionListener al : btnClose.getActionListeners()) {
                btnClose.removeActionListener(al);
            }

            btnClose.addActionListener(e -> {
                log("Menutup slot: " + deviceId);
                scrcpyService.stop(windowTitle);
                deviceSlotMap.remove(slotKey);

                lblTitle.setText(" Slot Kosong " + (targetIndex + 1));
                btnClose.setVisible(false);
                targetCanvas.repaint();
            });

            // Jalankan Scrcpy di background
            executor.submit(() -> {
                try {
                    boolean recordMode = (chkAutoRecord != null && chkAutoRecord.isSelected());
                    scrcpyService.start(deviceId, windowTitle, recordMode);

                    Thread.sleep(1000); // Jeda agar window siap
                    scrcpyService.embed(windowTitle, targetCanvas, this, spinX, spinY);

                } catch (Exception e) {
                    log("Error di Slot " + targetIndex + ": " + e.getMessage());
                    deviceSlotMap.remove(slotKey);
                }
            });
        });
    }

    private void duplicateDevice(String deviceId) {
        if (deviceId == null) {
            return;
        }
        log("Menduplikasi tampilan untuk: " + deviceId + "...");
        startScrcpyMirror(deviceId, true);
    }

    public void refreshDeviceList(List<String> devices) {
        SwingUtilities.invokeLater(() -> {
            DefaultListModel<String> model = new DefaultListModel<>();
            devices.forEach(model::addElement);
            jListDevices.setModel(model);

            panelLayar.revalidate();
            panelLayar.repaint();
            log("Device ditemukan: " + devices.size());
        });
    }

    // ================= LOGGING & WATCHER =================
    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append("> " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void setupLogStyle() {
        txtLog.setEditable(false);
        txtLog.setBackground(Color.BLACK);
        txtLog.setForeground(new Color(50, 255, 50));
        txtLog.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
    }

    private void startAutoDeviceWatcher() {
        executor.submit(() -> {
            while (true) {
                try {
                    Set<String> current = new HashSet<>(adbService.getConnectedDevices());

                    if (!current.equals(lastDevices)) {
                        lastDevices.clear();
                        lastDevices.addAll(current);
                        log("Perubahan device terdeteksi...");
                        refreshDeviceList(new ArrayList<>(current));
                    }
                    Thread.sleep(2000);
                } catch (Exception e) {
                    log("Watcher error: " + e.getMessage());
                }
            }
        });
    }

    // ================= GUI AUTO GENERATED BY NETBEANS =================
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panelTopBar = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        spinX = new javax.swing.JSpinner();
        spinY = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        panelDevices = new RoundedPanel(25);
        panelDevicesList = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        spDevices = new javax.swing.JScrollPane();
        jListDevices = new javax.swing.JList<>();
        btnDuplicate = new javax.swing.JButton();
        btnUSB = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        btnConnect = new javax.swing.JButton();
        btnRefresh = new javax.swing.JButton();
        btnConnectWiFi = new javax.swing.JButton();
        btnEnableTcp5555 = new javax.swing.JButton();
        panelControl = new javax.swing.JPanel();
        btnSendText = new javax.swing.JButton();
        chkSync = new javax.swing.JCheckBox();
        txtInputMasal = new javax.swing.JTextField();
        panelBulk = new javax.swing.JPanel();
        spLog = new javax.swing.JScrollPane();
        txtLog = new javax.swing.JTextArea();
        jPanel2 = new javax.swing.JPanel();
        btnReboot = new javax.swing.JButton();
        btnScreenshot = new javax.swing.JButton();
        btnRecordManager = new javax.swing.JButton();
        btnInstallAPK = new javax.swing.JButton();
        panelNav = new javax.swing.JPanel();
        btnRecentAll = new javax.swing.JButton();
        btnHomeAll = new javax.swing.JButton();
        btnBackAll = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setAlwaysOnTop(true);
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        setName("MainFrame"); // NOI18N
        setPreferredSize(new java.awt.Dimension(280, 668));
        setResizable(false);
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                formMouseDragged(evt);
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                formMousePressed(evt);
            }
        });

        panelTopBar.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 1, 1, 1));

        jLabel1.setFont(new java.awt.Font("Segoe UI Black", 2, 14)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("kunyuk.pro");
        jLabel1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        jLabel2.setForeground(new java.awt.Color(255, 255, 255));
        jLabel2.setText("X");

        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("Y");

        javax.swing.GroupLayout panelTopBarLayout = new javax.swing.GroupLayout(panelTopBar);
        panelTopBar.setLayout(panelTopBarLayout);
        panelTopBarLayout.setHorizontalGroup(
            panelTopBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelTopBarLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addGap(92, 92, 92)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(spinX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(3, 3, 3)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(spinY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panelTopBarLayout.setVerticalGroup(
            panelTopBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelTopBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(spinX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(spinY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel2)
                .addComponent(jLabel3)
                .addComponent(jLabel1))
        );

        getContentPane().add(panelTopBar, java.awt.BorderLayout.NORTH);

        panelDevices.setPreferredSize(new java.awt.Dimension(345, 640));
        panelDevices.setLayout(new javax.swing.BoxLayout(panelDevices, javax.swing.BoxLayout.Y_AXIS));

        panelDevicesList.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1), "Devices"));
        panelDevicesList.setRequestFocusEnabled(false);
        panelDevicesList.setLayout(new java.awt.GridLayout(2, 1, 5, 5));

        jPanel3.setLayout(new java.awt.BorderLayout(5, 5));

        jListDevices.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jListDevices.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jListDevicesMouseClicked(evt);
            }
        });
        spDevices.setViewportView(jListDevices);

        jPanel3.add(spDevices, java.awt.BorderLayout.CENTER);

        btnDuplicate.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnDuplicate.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/duplicat.png"))); // NOI18N
        btnDuplicate.setText("Duplicate HP");
        btnDuplicate.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnDuplicate.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnDuplicate.addActionListener(this::btnDuplicateActionPerformed);
        jPanel3.add(btnDuplicate, java.awt.BorderLayout.LINE_END);

        btnUSB.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnUSB.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/search.png"))); // NOI18N
        btnUSB.setText("Search USB Debug");
        btnUSB.addActionListener(this::btnUSBActionPerformed);
        jPanel3.add(btnUSB, java.awt.BorderLayout.PAGE_END);

        panelDevicesList.add(jPanel3);

        jPanel1.setLayout(new java.awt.GridLayout(2, 0, 5, 5));

        btnConnect.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnConnect.setText("Connect");
        btnConnect.addActionListener(this::btnConnectActionPerformed);
        jPanel1.add(btnConnect);

        btnRefresh.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnRefresh.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/refresh.png"))); // NOI18N
        btnRefresh.setText("Reset Devices");
        btnRefresh.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnRefresh.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnRefresh.addActionListener(this::btnRefreshActionPerformed);
        jPanel1.add(btnRefresh);

        btnConnectWiFi.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnConnectWiFi.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/wifi.png"))); // NOI18N
        btnConnectWiFi.setText("Wi-Fi Debug");
        btnConnectWiFi.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnConnectWiFi.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnConnectWiFi.addActionListener(this::btnConnectWiFiActionPerformed);
        jPanel1.add(btnConnectWiFi);

        btnEnableTcp5555.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnEnableTcp5555.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/adb.png"))); // NOI18N
        btnEnableTcp5555.setText("TCP IP");
        btnEnableTcp5555.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnEnableTcp5555.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnEnableTcp5555.addActionListener(this::btnEnableTcp5555ActionPerformed);
        jPanel1.add(btnEnableTcp5555);

        panelDevicesList.add(jPanel1);

        panelDevices.add(panelDevicesList);

        panelControl.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        btnSendText.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnSendText.setText("Send All");
        btnSendText.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnSendText.addActionListener(this::btnSendTextActionPerformed);

        chkSync.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        chkSync.setText("Sync All Devices");
        chkSync.addActionListener(this::chkSyncActionPerformed);

        javax.swing.GroupLayout panelControlLayout = new javax.swing.GroupLayout(panelControl);
        panelControl.setLayout(panelControlLayout);
        panelControlLayout.setHorizontalGroup(
            panelControlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelControlLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelControlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panelControlLayout.createSequentialGroup()
                        .addComponent(txtInputMasal)
                        .addGap(12, 12, 12)
                        .addComponent(btnSendText, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(15, 15, 15))
                    .addGroup(panelControlLayout.createSequentialGroup()
                        .addComponent(chkSync)
                        .addContainerGap(238, Short.MAX_VALUE))))
        );
        panelControlLayout.setVerticalGroup(
            panelControlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelControlLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelControlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtInputMasal, javax.swing.GroupLayout.DEFAULT_SIZE, 31, Short.MAX_VALUE)
                    .addComponent(btnSendText))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chkSync)
                .addGap(9, 9, 9))
        );

        panelDevices.add(panelControl);

        panelBulk.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        panelBulk.setPreferredSize(new java.awt.Dimension(325, 300));

        spLog.setViewportBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        spLog.setFont(new java.awt.Font("Segoe UI", 0, 8)); // NOI18N
        spLog.setRowHeaderView(null);
        spLog.setViewportView(null);

        txtLog.setColumns(20);
        txtLog.setFont(new java.awt.Font("Segoe UI", 0, 8)); // NOI18N
        txtLog.setForeground(new java.awt.Color(255, 255, 255));
        txtLog.setRows(5);
        txtLog.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        spLog.setViewportView(txtLog);

        jPanel2.setLayout(new java.awt.GridLayout(0, 4, 3, 5));

        btnReboot.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnReboot.setText("Reboot");
        btnReboot.addActionListener(this::btnRebootActionPerformed);
        jPanel2.add(btnReboot);

        btnScreenshot.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnScreenshot.setText("Screenshot");
        btnScreenshot.addActionListener(this::btnScreenshotActionPerformed);
        jPanel2.add(btnScreenshot);

        btnRecordManager.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnRecordManager.setText("Record");
        btnRecordManager.addActionListener(this::btnRecordManagerActionPerformed);
        jPanel2.add(btnRecordManager);

        btnInstallAPK.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnInstallAPK.setText("Install APK");
        btnInstallAPK.addActionListener(this::btnInstallAPKActionPerformed);
        jPanel2.add(btnInstallAPK);

        javax.swing.GroupLayout panelBulkLayout = new javax.swing.GroupLayout(panelBulk);
        panelBulk.setLayout(panelBulkLayout);
        panelBulkLayout.setHorizontalGroup(
            panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelBulkLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(spLog)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        panelBulkLayout.setVerticalGroup(
            panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelBulkLayout.createSequentialGroup()
                .addComponent(spLog, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(83, Short.MAX_VALUE))
        );

        panelDevices.add(panelBulk);

        panelNav.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        panelNav.setPreferredSize(new java.awt.Dimension(325, 70));

        btnRecentAll.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnRecentAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/view.png"))); // NOI18N
        btnRecentAll.setText("View");
        btnRecentAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnRecentAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnRecentAll.addActionListener(this::btnRecentAllActionPerformed);

        btnHomeAll.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnHomeAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/home.png"))); // NOI18N
        btnHomeAll.setText("Home");
        btnHomeAll.setDisabledIcon(null);
        btnHomeAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnHomeAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnHomeAll.addActionListener(this::btnHomeAllActionPerformed);

        btnBackAll.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnBackAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/undo.png"))); // NOI18N
        btnBackAll.setText("Back");
        btnBackAll.setDisabledIcon(null);
        btnBackAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnBackAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnBackAll.addActionListener(this::btnBackAllActionPerformed);

        javax.swing.GroupLayout panelNavLayout = new javax.swing.GroupLayout(panelNav);
        panelNav.setLayout(panelNavLayout);
        panelNavLayout.setHorizontalGroup(
            panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelNavLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(btnRecentAll)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 49, Short.MAX_VALUE)
                .addComponent(btnHomeAll)
                .addGap(48, 48, 48)
                .addComponent(btnBackAll)
                .addGap(14, 14, 14))
        );
        panelNavLayout.setVerticalGroup(
            panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelNavLayout.createSequentialGroup()
                .addGap(9, 9, 9)
                .addGroup(panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnRecentAll)
                    .addComponent(btnBackAll)))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelNavLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnHomeAll))
        );

        panelDevices.add(panelNav);

        getContentPane().add(panelDevices, java.awt.BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnRecentAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRecentAllActionPerformed
        List<String> targets = chkSync.isSelected() ? adbService.getConnectedDevices() : Collections.singletonList(jListDevices.getSelectedValue());
        if (targets.get(0) != null)
            adbService.sendKeyEvent("187", targets);
    }//GEN-LAST:event_btnRecentAllActionPerformed

    private void btnBackAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBackAllActionPerformed
        List<String> targets = chkSync.isSelected() ? adbService.getConnectedDevices() : Collections.singletonList(jListDevices.getSelectedValue());
        if (targets.get(0) != null)
            adbService.sendKeyEvent("4", targets);
    }//GEN-LAST:event_btnBackAllActionPerformed

    private void jListDevicesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jListDevicesMouseClicked
        if (evt.getClickCount() == 2) {
            // Dapatkan index item berdasarkan koordinat mouse
            int index = jListDevices.locationToIndex(evt.getPoint());

            // Pastikan klik benar-benar di atas sebuah item yang valid
            if (index >= 0 && jListDevices.getCellBounds(index, index).contains(evt.getPoint())) {
                String selectedID = jListDevices.getModel().getElementAt(index);
                startScrcpyMirror(selectedID, false);
            }
        }
    }//GEN-LAST:event_jListDevicesMouseClicked

    private void btnConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectActionPerformed
        String selectedID = jListDevices.getSelectedValue();
        if (selectedID != null) {
            startScrcpyMirror(selectedID, false); // false = bukan clone
        }
    }//GEN-LAST:event_btnConnectActionPerformed

    private void btnRebootActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRebootActionPerformed
        String selectedID = jListDevices.getSelectedValue();
        if (selectedID != null) {
            if (JOptionPane.showConfirmDialog(this, "Reboot HP " + selectedID + "?", "Konfirmasi Reboot", JOptionPane.YES_NO_OPTION) == 0) {
                adbService.rebootMassal(Collections.singletonList(selectedID));
                log("🔄 Memulai proses reboot untuk: " + selectedID);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Pilih HP di list perangkat terlebih dahulu!");
        }
    }//GEN-LAST:event_btnRebootActionPerformed

    private void btnScreenshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnScreenshotActionPerformed
        String selectedID = jListDevices.getSelectedValue();
        if (selectedID != null) {
            String folderPath = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "Mirror_Screenshots";
            new File(folderPath).mkdirs();
            
            // Masukkan 1 device yang dipilih ke dalam list tunggal
            adbService.screenshotMassal(folderPath, Collections.singletonList(selectedID));
            log("📸 Screenshot diambil untuk: " + selectedID);
        } else {
            JOptionPane.showMessageDialog(this, "Pilih HP di list perangkat terlebih dahulu!");
        }
    }//GEN-LAST:event_btnScreenshotActionPerformed

    private void btnInstallAPKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInstallAPKActionPerformed
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            adbService.installApkMassal(chooser.getSelectedFile().getAbsolutePath(), adbService.getConnectedDevices());
        }
    }//GEN-LAST:event_btnInstallAPKActionPerformed

    private void btnUSBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUSBActionPerformed
        adbService.scanNetworkForDevices();
    }//GEN-LAST:event_btnUSBActionPerformed

    private void btnConnectWiFiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectWiFiActionPerformed
        String[] ips = txtInputMasal.getText().trim().split(",");
        for (String ip : ips) {
            if (!ip.isEmpty()) {
                adbService.connectWifi(ip.contains(":") ? ip : ip + ":5555");
            }
        }
    }//GEN-LAST:event_btnConnectWiFiActionPerformed

    private void btnRefreshActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshActionPerformed
        executor.submit(() -> {
            try {
                scrcpyService.stopAll();
                deviceSlotMap.clear();
                Thread.sleep(800);

                // 🔥 CARI DATANYA DULU, BARU LEMPAR KE UI
                List<String> devices = adbService.getConnectedDevices();
                SwingUtilities.invokeLater(() -> refreshDeviceList(devices));

            } catch (Exception e) {
                log("Gagal refresh: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        });
    }//GEN-LAST:event_btnRefreshActionPerformed

    private void btnHomeAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnHomeAllActionPerformed
        List<String> targets = chkSync.isSelected() ? adbService.getConnectedDevices() : Collections.singletonList(jListDevices.getSelectedValue());
        if (targets.get(0) != null)
            adbService.sendKeyEvent("3", targets);
    }//GEN-LAST:event_btnHomeAllActionPerformed

    private void btnSendTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSendTextActionPerformed
        if (!txtInputMasal.getText().trim().isEmpty()) {
            adbService.sendTextMassal(txtInputMasal.getText(), adbService.getConnectedDevices());
            txtInputMasal.setText("");
        }
    }//GEN-LAST:event_btnSendTextActionPerformed

    private void btnEnableTcp5555ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEnableTcp5555ActionPerformed
        if (jListDevices.getSelectedValue() != null) {
            adbService.enableTcpIp(jListDevices.getSelectedValue());
        }
    }//GEN-LAST:event_btnEnableTcp5555ActionPerformed

    private void chkSyncActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkSyncActionPerformed
        if (chkSync.isSelected()) {
            syncService.updatePosition();
            syncService.setVisible(true);
            log("🟢 SYNC ON: Kaca penangkap layar aktif!");
        } else {
            syncService.setVisible(false);
            log("🔴 SYNC OFF");
        }
    }//GEN-LAST:event_chkSyncActionPerformed

    private void btnDuplicateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDuplicateActionPerformed
        String selectedID = jListDevices.getSelectedValue();
        if (selectedID != null) {
            // 🔥 Pastikan ini 'true' agar sistem tahu ini adalah cloning
            startScrcpyMirror(selectedID, true);
        } else {
            log("Pilih device di list dulu!");
        }
    }//GEN-LAST:event_btnDuplicateActionPerformed

    private void btnRecordManagerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRecordManagerActionPerformed
        if (recordControlFrame == null || !recordControlFrame.isVisible()) {
            recordControlFrame = new RecordControlFrame(this);
            recordControlFrame.setVisible(true);
        } else {
            // Jika sudah terbuka, bawa ke depan layar
            recordControlFrame.toFront();
            recordControlFrame.requestFocus();
        }
    }//GEN-LAST:event_btnRecordManagerActionPerformed

    private void formMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMousePressed
        mouseX = evt.getX();
        mouseY = evt.getY();
    }//GEN-LAST:event_formMousePressed

    private void formMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseDragged
        int x = evt.getXOnScreen();
        int y = evt.getYOnScreen();
        setLocation(x - mouseX, y - mouseY);
    }//GEN-LAST:event_formMouseDragged

    public static void main(String args[]) {

        try {
            // 1. Aktifkan Skincare macOS Dark
            com.formdev.flatlaf.themes.FlatMacDarkLaf.setup();

            // 2. Tweak UI: Bikin sudut tombol dan kotak input lebih bulat (Apple Style)
            javax.swing.UIManager.put("Button.arc", 15);
            javax.swing.UIManager.put("Component.arc", 15);
            javax.swing.UIManager.put("TextComponent.arc", 15);

            // 3. Tweak ScrollBar agar lebih tipis dan modern
            javax.swing.UIManager.put("ScrollBar.thumbArc", 999);
            javax.swing.UIManager.put("ScrollBar.width", 10);

        } catch (Exception ex) {
            System.err.println("Gagal memuat tema Mac: " + ex.getMessage());
        }

        java.awt.EventQueue.invokeLater(() -> {
            new MainDashboard().setVisible(true);
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnBackAll;
    private javax.swing.JButton btnConnect;
    private javax.swing.JButton btnConnectWiFi;
    private javax.swing.JButton btnDuplicate;
    private javax.swing.JButton btnEnableTcp5555;
    private javax.swing.JButton btnHomeAll;
    private javax.swing.JButton btnInstallAPK;
    private javax.swing.JButton btnReboot;
    private javax.swing.JButton btnRecentAll;
    private javax.swing.JButton btnRecordManager;
    private javax.swing.JButton btnRefresh;
    private javax.swing.JButton btnScreenshot;
    private javax.swing.JButton btnSendText;
    private javax.swing.JButton btnUSB;
    private javax.swing.JCheckBox chkSync;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JList<String> jListDevices;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel panelBulk;
    private javax.swing.JPanel panelControl;
    private javax.swing.JPanel panelDevices;
    private javax.swing.JPanel panelDevicesList;
    private javax.swing.JPanel panelNav;
    private javax.swing.JPanel panelTopBar;
    private javax.swing.JScrollPane spDevices;
    private javax.swing.JScrollPane spLog;
    private javax.swing.JSpinner spinX;
    private javax.swing.JSpinner spinY;
    private javax.swing.JTextField txtInputMasal;
    private javax.swing.JTextArea txtLog;
    // End of variables declaration//GEN-END:variables
}
