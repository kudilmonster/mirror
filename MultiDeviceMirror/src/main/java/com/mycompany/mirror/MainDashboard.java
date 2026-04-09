package com.mycompany.mirror;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.filechooser.FileNameExtensionFilter;

public class MainDashboard extends JFrame {

    // ================= CONFIG =================
    private final String basePath = System.getProperty("user.dir") + "\\scrcpy\\";
    private final String scrcpyExecutable = basePath + "scrcpy.exe";

    // ================= STATE =================
    private final Set<String> activeDevices = new HashSet<>();
    private final Set<String> lastDevices = new HashSet<>();
    // Tambahkan ini untuk mengunci HP ke slot spesifik
    private final Map<String, Integer> deviceSlotMap = new HashMap<>();

    private ScrcpyService scrcpyService;
    private SyncService syncService;
    public AdbService adbService;
    public final String adbExecutable = basePath + "adb.exe";
    public final ExecutorService executor = Executors.newFixedThreadPool(10);

    // ================= CONSTRUCTOR =================
    public MainDashboard() {
        initComponents();
        initServices();
        initUI();
//        initListeners();
        startAutoDeviceWatcher();
    }

    private void initServices() {
        scrcpyService = new ScrcpyService(scrcpyExecutable);
        adbService = new AdbService(this, adbExecutable, executor); // 🔥 INISIALISASI ADB
        syncService = new SyncService(this, panelLayar, chkSync); // INISIALISASI SYNC
    }

    private void initUI() {
        // Bersihkan layout bawaan NetBeans
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());

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
        panelDevices.setBackground(new Color(245, 246, 250));
        panelDevices.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 1));

        panelLayar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panelLayar.setBackground(Color.WHITE);

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
    // 1. DYNAMIC GRID MAKER (AMAN UNTUK JNA)
    // ==========================================================
    private void syncGridPanels(int activeCount) {
        int minSlots = 3;
        int requiredSlots = Math.max(minSlots, activeCount);
        int currentSlots = panelLayar.getComponentCount();

        // Jika jumlah kotak sudah pas atau lebih, jangan lakukan apa-apa
        if (currentSlots >= requiredSlots) {
            return;
        }

        // 🔥 PENTING: Jangan gunakan removeAll() agar Canvas JNA tidak hancur!
        panelLayar.setLayout(new GridLayout(0, 3, 10, 10));

        // Tambahkan HANYA slot yang kurang
        for (int i = currentSlots; i < requiredSlots; i++) {
            JPanel cell = new JPanel(new BorderLayout());
            cell.setBackground(new Color(25, 25, 25));
            cell.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50)));

            Canvas canvas = new Canvas();
            canvas.setBackground(Color.BLACK);

            cell.add(canvas, BorderLayout.CENTER);
            panelLayar.add(cell);
        }

        panelLayar.revalidate();
        panelLayar.repaint();
    }

    //================= CORE =================
    private void startScrcpyMirror(String deviceId) {
        if (deviceId == null) {
            return;
        }

        // Cek apakah device sudah di-booking slotnya
        if (deviceSlotMap.containsKey(deviceId)) {
            log("Device " + deviceId + " sudah berjalan.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            // 1. Cari slot kosong dengan urutan prioritas: Kanan (2), Kiri (0), Tengah (1)
            int targetIndex = -1;
            int[] prioritySlots = {2, 0, 1};

            for (int slot : prioritySlots) {
                if (!deviceSlotMap.containsValue(slot)) {
                    targetIndex = slot;
                    break;
                }
            }

            // Jika 3 slot utama penuh, buat kotak/index baru di urutan selanjutnya
            if (targetIndex == -1) {
                targetIndex = Math.max(3, deviceSlotMap.size());
            }

            // 2. Kunci permanen device ini ke slot yang ditemukan
            deviceSlotMap.put(deviceId, targetIndex);

            // 3. Pastikan grid punya cukup kotak (Tanpa menghancurkan kotak lama)
            syncGridPanels(targetIndex + 1);

            // 4. Ambil Canvas target dari dalam kotak
            JPanel cell = (JPanel) panelLayar.getComponent(targetIndex);
            Canvas targetCanvas = (Canvas) cell.getComponent(0);

            executor.submit(() -> {
                try {
                    String windowTitle = "Scrcpy_" + deviceId;
                    scrcpyService.start(deviceId, windowTitle);

                    // Di dalam MainDashboard.java
                    scrcpyService.embed(windowTitle, targetCanvas, this, spinX, spinY);

                } catch (Exception e) {
                    log("Error: " + e.getMessage());
                    deviceSlotMap.remove(deviceId);
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
        btnConnectWiFi.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/wifi_add.svg"));
        btnEnableTcp5555 = new javax.swing.JButton();
        btnUSB = new javax.swing.JButton();
        btnConnect = new javax.swing.JButton();
        spDevices = new javax.swing.JScrollPane();
        jListDevices = new javax.swing.JList<>();
        btnRefresh = new javax.swing.JButton();
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
        btnRecentAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/menu.svg"));
        btnHomeAll = new javax.swing.JButton();
        btnHomeAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/home.svg"));
        btnBackAll = new javax.swing.JButton();
        btnBackAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/backspace.svg"));
        panelTopBar = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        spinX = new javax.swing.JSpinner();
        spinY = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        panelLayar = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setName("MainFrame"); // NOI18N
        setPreferredSize(new java.awt.Dimension(1000, 800));

        panelDevices.setBackground(new java.awt.Color(255, 255, 255));
        panelDevices.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10));
        panelDevices.setPreferredSize(new java.awt.Dimension(345, 640));
        panelDevices.setLayout(new javax.swing.BoxLayout(panelDevices, javax.swing.BoxLayout.Y_AXIS));

        panelDevicesList.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1), "Devices"));
        panelDevicesList.setRequestFocusEnabled(false);

        btnConnectWiFi.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/wifi_add.svg"));
        btnConnectWiFi.setText("Wi-Fi Debug");
        btnConnectWiFi.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnConnectWiFi.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnConnectWiFi.addActionListener(this::btnConnectWiFiActionPerformed);

        btnEnableTcp5555.setText("TCP");
        btnEnableTcp5555.addActionListener(this::btnEnableTcp5555ActionPerformed);

        btnUSB.setText("USB Debug");
        btnUSB.addActionListener(this::btnUSBActionPerformed);

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

        btnRefresh.setText("Refresh Devices");
        btnRefresh.addActionListener(this::btnRefreshActionPerformed);

        javax.swing.GroupLayout panelDevicesListLayout = new javax.swing.GroupLayout(panelDevicesList);
        panelDevicesList.setLayout(panelDevicesListLayout);
        panelDevicesListLayout.setHorizontalGroup(
            panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelDevicesListLayout.createSequentialGroup()
                .addComponent(spDevices, javax.swing.GroupLayout.DEFAULT_SIZE, 180, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addGroup(panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(btnRefresh, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnConnect, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnUSB, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(5, 5, 5))
            .addGroup(panelDevicesListLayout.createSequentialGroup()
                .addComponent(btnConnectWiFi, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnEnableTcp5555, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                        .addComponent(btnRefresh)))
                .addGap(18, 18, 18)
                .addGroup(panelDevicesListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(panelDevicesListLayout.createSequentialGroup()
                        .addComponent(btnUSB)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnEnableTcp5555))
                    .addComponent(btnConnectWiFi))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        panelDevices.add(panelDevicesList);

        panelControl.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(153, 153, 153), 2, true));

        btnSendText.setText("Send All");
        btnSendText.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnSendText.addActionListener(this::btnSendTextActionPerformed);

        chkSync.setText("Sync All Devices");
        chkSync.addActionListener(this::chkSyncActionPerformed);

        javax.swing.GroupLayout panelControlLayout = new javax.swing.GroupLayout(panelControl);
        panelControl.setLayout(panelControlLayout);
        panelControlLayout.setHorizontalGroup(
            panelControlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelControlLayout.createSequentialGroup()
                .addGroup(panelControlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panelControlLayout.createSequentialGroup()
                        .addComponent(chkSync)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(panelControlLayout.createSequentialGroup()
                        .addComponent(txtInputMasal, javax.swing.GroupLayout.DEFAULT_SIZE, 187, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(btnSendText, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)))
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

        btnInstallAPK.setText("Install APK");
        btnInstallAPK.addActionListener(this::btnInstallAPKActionPerformed);

        btnScreenshotAll.setText("Screenshot All");
        btnScreenshotAll.addActionListener(this::btnScreenshotAllActionPerformed);

        btnRebootAll.setText("Reboot All");
        btnRebootAll.addActionListener(this::btnRebootAllActionPerformed);

        spLog.setViewportBorder(new javax.swing.border.LineBorder(new java.awt.Color(255, 255, 255), 1, true));
        spLog.setFont(new java.awt.Font("Segoe UI", 0, 8)); // NOI18N
        spLog.setRowHeaderView(null);
        spLog.setViewportView(null);

        txtLog.setColumns(20);
        txtLog.setFont(new java.awt.Font("Segoe UI", 0, 8)); // NOI18N
        txtLog.setForeground(new java.awt.Color(255, 255, 255));
        txtLog.setRows(5);
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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnScreenshotAll)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRebootAll)
                        .addGap(0, 23, Short.MAX_VALUE)))
                .addContainerGap())
        );
        panelBulkLayout.setVerticalGroup(
            panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelBulkLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(spLog, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 130, Short.MAX_VALUE)
                .addGroup(panelBulkLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnInstallAPK)
                    .addComponent(btnScreenshotAll)
                    .addComponent(btnRebootAll))
                .addContainerGap())
        );

        panelDevices.add(panelBulk);

        panelNav.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(153, 153, 153), 2, true));

        btnRecentAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/menu.svg")
        );
        btnRecentAll.setText("Recent");
        btnRecentAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnRecentAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnRecentAll.addActionListener(this::btnRecentAllActionPerformed);

        btnHomeAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/home.svg")
        );
        btnHomeAll.setText("Home");
        btnHomeAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnHomeAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnHomeAll.addActionListener(this::btnHomeAllActionPerformed);

        btnBackAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/backspace.svg")
        );
        btnBackAll.setText("Back");
        btnBackAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnBackAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnBackAll.addActionListener(this::btnBackAllActionPerformed);

        javax.swing.GroupLayout panelNavLayout = new javax.swing.GroupLayout(panelNav);
        panelNav.setLayout(panelNavLayout);
        panelNavLayout.setHorizontalGroup(
            panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelNavLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnRecentAll)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 50, Short.MAX_VALUE)
                .addComponent(btnHomeAll)
                .addGap(26, 26, 26)
                .addComponent(btnBackAll)
                .addGap(23, 23, 23))
        );
        panelNavLayout.setVerticalGroup(
            panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelNavLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnHomeAll)
                    .addGroup(panelNavLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(btnRecentAll, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(btnBackAll)))
                .addContainerGap())
        );

        panelDevices.add(panelNav);

        getContentPane().add(panelDevices, java.awt.BorderLayout.WEST);

        panelTopBar.setBackground(new java.awt.Color(30, 30, 30));
        panelTopBar.setPreferredSize(new java.awt.Dimension(1000, 30));

        jLabel1.setFont(new java.awt.Font("Segoe UI", 0, 18)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("kunyuk.pro");
        jLabel1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(255, 255, 255));
        jLabel2.setText("X");

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("Y");

        javax.swing.GroupLayout panelTopBarLayout = new javax.swing.GroupLayout(panelTopBar);
        panelTopBar.setLayout(panelTopBarLayout);
        panelTopBarLayout.setHorizontalGroup(
            panelTopBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelTopBarLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addGap(84, 84, 84)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(spinX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(3, 3, 3)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(spinY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(660, Short.MAX_VALUE))
        );
        panelTopBarLayout.setVerticalGroup(
            panelTopBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelTopBarLayout.createSequentialGroup()
                .addGroup(panelTopBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(spinX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spinY, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3))
                .addGap(0, 4, Short.MAX_VALUE))
        );

        getContentPane().add(panelTopBar, java.awt.BorderLayout.NORTH);

        panelLayar.setLayout(new java.awt.GridLayout(1, 3));
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
            startScrcpyMirror(jListDevices.getSelectedValue());
        }
    }//GEN-LAST:event_jListDevicesMouseClicked

    private void btnConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectActionPerformed
// Ambil ID dari JList saja, bukan dari kotak teks
        String selectedID = jListDevices.getSelectedValue();

        if (selectedID == null) {
            log("Silakan pilih perangkat!");
            return;
        }

        startScrcpyMirror(selectedID);
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
