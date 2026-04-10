package com.mycompany.mirror;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.filechooser.FileNameExtensionFilter;

public class MainDashboard extends JFrame {

    private int nextSlotIndex = 0;
    // ================= CONFIG =================
    
    private final String basePath = System.getProperty("user.dir") + "\\scrcpy\\";
    private final String scrcpyExecutable = basePath + "scrcpy.exe";
    public final String adbExecutable = basePath + "adb.exe";     
    //==================================================
    private ScrcpyService scrcpyService;
    private SyncService syncService;
    public AdbService adbService;

    // ================= STATE =================
    private final Set<String> activeDevices = new HashSet<>();
    private final Set<String> lastDevices = new HashSet<>();
    // Tambahkan ini untuk mengunci HP ke slot spesifik
    private final Map<String, Integer> deviceSlotMap = new HashMap<>();
    public final ExecutorService executor = Executors.newFixedThreadPool(10);

    // ================= CONSTRUCTOR =================
    public MainDashboard() {
        initComponents();
        initServices();
        initUI();
        startAutoDeviceWatcher();
    }

    private void initServices() {

        scrcpyService = new ScrcpyService(scrcpyExecutable, executor, this);
        adbService = new AdbService(this, adbExecutable, executor); // 🔥 INISIALISASI ADB
        syncService = new SyncService(this, panelLayar, chkSync); // INISIALISASI SYNC
    }

    private void initUI() {
        // Bersihkan layout bawaan NetBeans
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        // 🔥 BUNGKUS PANEL LAYAR DENGAN SCROLLPANE
        JScrollPane scrollLayar = new JScrollPane(panelLayar);
        scrollLayar.setBorder(BorderFactory.createEmptyBorder()); // Hilangkan garis tepi agar bersih
        scrollLayar.getVerticalScrollBar().setUnitIncrement(20);  // Bikin scroll mouse lebih cepat & mulus
        scrollLayar.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // Kunci agar tidak bisa geser kiri-kanan

        // Pasang kembali panel ke posisinya
        getContentPane().add(panelTopBar, BorderLayout.NORTH);
        getContentPane().add(panelDevices, BorderLayout.WEST);
        getContentPane().add(scrollLayar, BorderLayout.CENTER);  // Masukkan ScrollPane ke tengah, bukan panelLayar langsung

        // Styling sisa panel
        panelDevices.setBackground(Color.LIGHT_GRAY);
        panelDevices.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 1));

        panelLayar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panelLayar.setBackground(Color.LIGHT_GRAY);

        setupLogStyle();
        refreshDeviceList();

        getContentPane().revalidate();
        getContentPane().repaint();
        // ======== INIT FITUR CLONE/SYNC ========
// Pantau pergerakan jendela aplikasi agar kaca overlay tidak tertinggal
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                if (syncService != null) {
                    syncService.updatePosition();
                }
            }

            @Override
            public void componentResized(ComponentEvent e) {
                if (syncService != null) {
                    syncService.updatePosition();
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log("Mematikan semua sistem...");
                shutdown();
                System.exit(0); // Tutup Java sepenuhnya
            }
        });
    }

    //===================================
    private void shutdown() {
        scrcpyService.stopAll();
        executor.shutdownNow();
    }

// ==========================================================
    // DYNAMIC GRID MAKER
    // ==========================================================
    private void syncGridPanels(int requiredCount) {
        int currentCount = panelLayar.getComponentCount();

        // 🔥 Kunci permanen di 3 kolom
        panelLayar.setLayout(new java.awt.GridLayout(0, 3, 10, 10));

        if (currentCount < requiredCount) {
            for (int i = currentCount; i < requiredCount; i++) {
                JPanel cell = new JPanel(new java.awt.BorderLayout());
                cell.setBackground(java.awt.Color.BLACK);
                cell.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(50, 50, 50)));

                java.awt.Canvas c = new java.awt.Canvas();
                c.setBackground(java.awt.Color.BLACK);
                cell.add(c, java.awt.BorderLayout.CENTER);

                panelLayar.add(cell);
            }
        }

        panelLayar.revalidate();
        panelLayar.repaint();
    }

    // ==========================================================
    // CORE MIRROR: Sekarang mendukung Duplikasi (Multi-Instance)
    // ==========================================================
    private void startScrcpyMirror(String deviceId, boolean isClone) {
        if (deviceId == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            // 1. Cari Slot Kosong
            int tempIndex = -1;
            int totalComponents = panelLayar.getComponentCount();

            // Cek Map untuk melihat slot mana yang sudah terisi
            for (int i = 0; i < 9; i++) {
                if (!deviceSlotMap.containsValue(i)) {
                    tempIndex = i;
                    break;
                }
            }

            if (tempIndex == -1) {
                tempIndex = totalComponents;
            }

            // 2. Kunci variabel untuk Lambda
            final int targetIndex = tempIndex;
            final String slotKey = isClone ? deviceId + "_clone_" + System.currentTimeMillis() : deviceId;

            log("Membuka Slot " + targetIndex + " untuk: " + deviceId);
            deviceSlotMap.put(slotKey, targetIndex);

            // 3. Pastikan Grid siap
            syncGridPanels(targetIndex + 1);

            // Ambil Canvas dari kotak target
            JPanel cell = (JPanel) panelLayar.getComponent(targetIndex);
            Canvas targetCanvas = (Canvas) cell.getComponent(0);

            // 4. Jalankan Scrcpy di background thread
            executor.submit(() -> {
                try {
                    // Berikan judul jendela yang unik dan mudah dicari
                    String windowTitle = "Mirror_" + targetIndex + "_" + (isClone ? "CLONE" : "ORIG");

                    // Start Scrcpy (dengan port unik)
                    scrcpyService.start(deviceId, windowTitle);

                    // Berikan jeda 1 detik agar proses OS benar-benar muncul sebelum di-embed
                    Thread.sleep(1000);

                    // Tempelkan ke UI
                    scrcpyService.embed(windowTitle, targetCanvas, this, spinX, spinY);

                } catch (Exception e) {
                    log("Error di Slot " + targetIndex + ": " + e.getMessage());
                    deviceSlotMap.remove(slotKey);
                }
            });
        });
    }

    // ==========================================================
    // FITUR DUPLIKAT: Membuka HP yang sama di slot baru
    // ==========================================================
    private void duplicateDevice(String deviceId) {
        if (deviceId == null) {
            return;
        }

        log("Menduplikasi tampilan untuk: " + deviceId + "...");

        // Kita buat ID bayangan agar sistem slot kita tidak menganggap ini device yang sama
        // Contoh: "RR8X102870L" menjadi "RR8X102870L_clone_1"
        int cloneCount = 1;
        String cloneId = deviceId + "_clone_" + cloneCount;

        while (deviceSlotMap.containsKey(cloneId)) {
            cloneCount++;
            cloneId = deviceId + "_clone_" + cloneCount;
        }

        final String finalCloneId = cloneId;

        SwingUtilities.invokeLater(() -> {
            // Cari slot kosong
            int targetIndex = -1;
            int currentSlots = panelLayar.getComponentCount();

            // Cari slot yang benar-benar belum terisi Canvas aktif
            for (int i = 0; i < currentSlots; i++) {
                if (!deviceSlotMap.containsValue(i)) {
                    targetIndex = i;
                    break;
                }
            }

            // Jika tidak ada slot kosong di grid 3x1, tambah slot baru
            if (targetIndex == -1) {
                targetIndex = currentSlots;
            }

            deviceSlotMap.put(finalCloneId, targetIndex);
            syncGridPanels(targetIndex + 1);

            JPanel cell = (JPanel) panelLayar.getComponent(targetIndex);
            Canvas targetCanvas = (Canvas) cell.getComponent(0);

            executor.submit(() -> {
                try {
                    // Gunakan judul jendela unik agar JNA tidak bingung
                    String windowTitle = "Scrcpy_Clone_" + System.currentTimeMillis();

                    // Jalankan scrcpy untuk deviceId asli tapi ke windowTitle baru
                    scrcpyService.start(deviceId, windowTitle);

                    // Tanamkan ke canvas
                    scrcpyService.embed(windowTitle, targetCanvas, this, spinX, spinY);

                } catch (Exception e) {
                    log("Gagal Duplikasi: " + e.getMessage());
                    deviceSlotMap.remove(finalCloneId);
                }
            });
        });
    }

    public void refreshDeviceList() {
// 🔥 FIX: Panggil melalui adbService
        List<String> devices = adbService.getConnectedDevices();

        DefaultListModel<String> model = new DefaultListModel<>();
        devices.forEach(model::addElement);

        jListDevices.setModel(model);
        activeDevices.clear();
        panelLayar.revalidate();
        panelLayar.repaint();
        log("Device ditemukan: " + devices.size());
    }

    // ================= UTIL =================
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
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 10));
    }

    private void startAutoDeviceWatcher() {
        executor.submit(() -> {
            while (true) {
                try {
                    // 🔥 FIX: Panggil melalui adbService
                    Set<String> current = new HashSet<>(adbService.getConnectedDevices());

                    if (!current.equals(lastDevices)) {
                        lastDevices.clear();
                        lastDevices.addAll(current);

                        log("Perubahan device terdeteksi...");
                        SwingUtilities.invokeLater(this::refreshDeviceList);
                    }

                    Thread.sleep(2000);

                } catch (Exception e) {
                    log("Watcher error: " + e.getMessage());
                }
            }
        });
    }

    // ================= [ ACTION LISTENERS ] =================
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panelDevices = new javax.swing.JPanel();
        panelDevicesList = new javax.swing.JPanel();
        btnConnectWiFi = new javax.swing.JButton();
        btnEnableTcp5555 = new javax.swing.JButton();
        btnUSB = new javax.swing.JButton();
        btnConnect = new javax.swing.JButton();
        spDevices = new javax.swing.JScrollPane();
        jListDevices = new javax.swing.JList<>();
        btnRefresh = new javax.swing.JButton();
        btnDuplicate = new javax.swing.JButton();
        panelControl = new javax.swing.JPanel();
        btnSendText = new javax.swing.JButton();
        chkSync = new javax.swing.JCheckBox();
        txtInputMasal = new javax.swing.JTextField();
        panelBulk = new javax.swing.JPanel();
        btnInstallAPK = new javax.swing.JButton();
        btnScreenshotAll = new javax.swing.JButton();
        btnRebootAll = new javax.swing.JButton();
        spLog = new javax.swing.JScrollPane();
        txtLog = new javax.swing.JTextArea();
        panelNav = new javax.swing.JPanel();
        btnRecentAll = new javax.swing.JButton();
        btnHomeAll = new javax.swing.JButton();
        btnBackAll = new javax.swing.JButton();
        panelTopBar = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        spinX = new javax.swing.JSpinner();
        spinY = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        panelLayar = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setName("MainFrame"); // NOI18N

        panelDevices.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        panelDevices.setPreferredSize(new java.awt.Dimension(345, 640));
        panelDevices.setLayout(new javax.swing.BoxLayout(panelDevices, javax.swing.BoxLayout.Y_AXIS));

        panelDevicesList.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1), "Devices"));
        panelDevicesList.setRequestFocusEnabled(false);

        btnConnectWiFi.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnConnectWiFi.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/wifi.png"))); // NOI18N
        btnConnectWiFi.setText("Wi-Fi Debug");
        btnConnectWiFi.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnConnectWiFi.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnConnectWiFi.addActionListener(this::btnConnectWiFiActionPerformed);

        btnEnableTcp5555.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnEnableTcp5555.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/adb.png"))); // NOI18N
        btnEnableTcp5555.setText("ADB");
        btnEnableTcp5555.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnEnableTcp5555.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnEnableTcp5555.addActionListener(this::btnEnableTcp5555ActionPerformed);

        btnUSB.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnUSB.setText("USB Debug");
        btnUSB.addActionListener(this::btnUSBActionPerformed);

        btnConnect.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnConnect.setText("Connect");
        btnConnect.addActionListener(this::btnConnectActionPerformed);

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

        btnRefresh.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnRefresh.setText("Refresh Devices");
        btnRefresh.addActionListener(this::btnRefreshActionPerformed);

        btnDuplicate.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnDuplicate.setText("Duplicate HP");
        btnDuplicate.addActionListener(this::btnDuplicateActionPerformed);

        javax.swing.GroupLayout panelDevicesListLayout = new javax.swing.GroupLayout(panelDevicesList);
        panelDevicesList.setLayout(panelDevicesListLayout);
        panelDevicesListLayout.setHorizontalGroup(
            panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelDevicesListLayout.createSequentialGroup()
                .addGroup(panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panelDevicesListLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(btnConnectWiFi)
                        .addGap(32, 32, 32)
                        .addComponent(btnEnableTcp5555, javax.swing.GroupLayout.PREFERRED_SIZE, 65, Short.MAX_VALUE))
                    .addComponent(spDevices))
                .addGap(18, 18, 18)
                .addGroup(panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnDuplicate, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnRefresh, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnConnect, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnUSB, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        panelDevicesListLayout.setVerticalGroup(
            panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelDevicesListLayout.createSequentialGroup()
                .addGroup(panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(spDevices, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(panelDevicesListLayout.createSequentialGroup()
                        .addComponent(btnConnect)
                        .addGap(18, 18, 18)
                        .addComponent(btnDuplicate)))
                .addGap(18, 18, 18)
                .addGroup(panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnEnableTcp5555)
                    .addComponent(btnConnectWiFi)
                    .addGroup(panelDevicesListLayout.createSequentialGroup()
                        .addComponent(btnRefresh)
                        .addGap(18, 18, 18)
                        .addComponent(btnUSB)))
                .addContainerGap())
        );

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
                        .addComponent(txtInputMasal, javax.swing.GroupLayout.DEFAULT_SIZE, 189, Short.MAX_VALUE)
                        .addGap(12, 12, 12)
                        .addComponent(btnSendText, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(panelControlLayout.createSequentialGroup()
                        .addComponent(chkSync)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
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

        btnInstallAPK.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnInstallAPK.setText("Install APK");
        btnInstallAPK.addActionListener(this::btnInstallAPKActionPerformed);

        btnScreenshotAll.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnScreenshotAll.setText("Screenshot All");
        btnScreenshotAll.addActionListener(this::btnScreenshotAllActionPerformed);

        btnRebootAll.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnRebootAll.setText("Reboot All");
        btnRebootAll.addActionListener(this::btnRebootAllActionPerformed);

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

        javax.swing.GroupLayout panelBulkLayout = new javax.swing.GroupLayout(panelBulk);
        panelBulk.setLayout(panelBulkLayout);
        panelBulkLayout.setHorizontalGroup(
            panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelBulkLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(spLog)
                    .addGroup(panelBulkLayout.createSequentialGroup()
                        .addComponent(btnInstallAPK)
                        .addGap(28, 28, 28)
                        .addComponent(btnScreenshotAll)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 28, Short.MAX_VALUE)
                        .addComponent(btnRebootAll)))
                .addContainerGap())
        );
        panelBulkLayout.setVerticalGroup(
            panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelBulkLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(spLog, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 127, Short.MAX_VALUE)
                .addGroup(panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnInstallAPK)
                    .addComponent(btnScreenshotAll)
                    .addComponent(btnRebootAll))
                .addContainerGap())
        );

        panelDevices.add(panelBulk);

        panelNav.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(51, 51, 51)));

        btnRecentAll.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnRecentAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/menu.png"))); // NOI18N
        btnRecentAll.setText("Recent");
        btnRecentAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnRecentAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnRecentAll.addActionListener(this::btnRecentAllActionPerformed);

        btnHomeAll.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnHomeAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/home.png"))); // NOI18N
        btnHomeAll.setText("Home");
        btnHomeAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnHomeAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnHomeAll.addActionListener(this::btnHomeAllActionPerformed);

        btnBackAll.setFont(new java.awt.Font("Segoe UI Semibold", 0, 10)); // NOI18N
        btnBackAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/undo.png"))); // NOI18N
        btnBackAll.setText("Back");
        btnBackAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnBackAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnBackAll.addActionListener(this::btnBackAllActionPerformed);

        javax.swing.GroupLayout panelNavLayout = new javax.swing.GroupLayout(panelNav);
        panelNav.setLayout(panelNavLayout);
        panelNavLayout.setHorizontalGroup(
            panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelNavLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnRecentAll)
                .addGap(44, 44, 44)
                .addComponent(btnHomeAll)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 51, Short.MAX_VALUE)
                .addComponent(btnBackAll)
                .addContainerGap())
        );
        panelNavLayout.setVerticalGroup(
            panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelNavLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnBackAll)
                    .addComponent(btnHomeAll)
                    .addComponent(btnRecentAll, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );

        panelDevices.add(panelNav);

        getContentPane().add(panelDevices, java.awt.BorderLayout.WEST);

        panelTopBar.setBackground(new java.awt.Color(30, 30, 30));
        panelTopBar.setPreferredSize(new java.awt.Dimension(1000, 30));

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
                .addContainerGap(667, Short.MAX_VALUE))
        );
        panelTopBarLayout.setVerticalGroup(
            panelTopBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelTopBarLayout.createSequentialGroup()
                .addGroup(panelTopBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spinX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spinY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(jLabel1))
                .addGap(0, 8, Short.MAX_VALUE))
        );

        getContentPane().add(panelTopBar, java.awt.BorderLayout.NORTH);

        panelLayar.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        javax.swing.GroupLayout panelLayarLayout = new javax.swing.GroupLayout(panelLayar);
        panelLayar.setLayout(panelLayarLayout);
        panelLayarLayout.setHorizontalGroup(
            panelLayarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 653, Short.MAX_VALUE)
        );
        panelLayarLayout.setVerticalGroup(
            panelLayarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 638, Short.MAX_VALUE)
        );

        getContentPane().add(panelLayar, java.awt.BorderLayout.CENTER);

        pack();
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
            startScrcpyMirror(jListDevices.getSelectedValue(), false);
        }
    }//GEN-LAST:event_jListDevicesMouseClicked

    private void btnConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectActionPerformed
        String selectedID = jListDevices.getSelectedValue();
        if (selectedID != null) {
            startScrcpyMirror(selectedID, false); // false = bukan clone
        }
    }//GEN-LAST:event_btnConnectActionPerformed

    private void btnRebootAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRebootAllActionPerformed
        List<String> devices = adbService.getConnectedDevices();
        if (!devices.isEmpty() && JOptionPane.showConfirmDialog(this, "Reboot " + devices.size() + " HP?", "Reboot", JOptionPane.YES_NO_OPTION) == 0) {
            adbService.rebootMassal(devices);
        }
    }//GEN-LAST:event_btnRebootAllActionPerformed

    private void btnScreenshotAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnScreenshotAllActionPerformed
        String folderPath = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "Mirror_Screenshots";
        new File(folderPath).mkdirs();
        adbService.screenshotMassal(folderPath, adbService.getConnectedDevices());
    }//GEN-LAST:event_btnScreenshotAllActionPerformed

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
                SwingUtilities.invokeLater(this::refreshDeviceList);
            } catch (Exception e) {
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
    private javax.swing.JButton btnRebootAll;
    private javax.swing.JButton btnRecentAll;
    private javax.swing.JButton btnRefresh;
    private javax.swing.JButton btnScreenshotAll;
    private javax.swing.JButton btnSendText;
    private javax.swing.JButton btnUSB;
    private javax.swing.JCheckBox chkSync;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JList<String> jListDevices;
    private javax.swing.JPanel panelBulk;
    private javax.swing.JPanel panelControl;
    private javax.swing.JPanel panelDevices;
    private javax.swing.JPanel panelDevicesList;
    private javax.swing.JPanel panelLayar;
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
