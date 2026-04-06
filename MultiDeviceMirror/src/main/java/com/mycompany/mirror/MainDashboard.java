package com.mycompany.mirror;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import java.awt.event.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainDashboard extends javax.swing.JFrame {

    private final int OFFSET_X = 7;  // geser kanan (+) / kiri (-)
    private final int OFFSET_Y = 4;  // geser bawah (+) / atas (-)

    private final int COLS = 3;
    private final int GAP = 10;
    // --- PENGATURAN PATH ---
    private final String basePath = System.getProperty("user.dir") + "\\scrcpy\\";
    private final String adbPath = basePath + "adb.exe";
    private final String scrcpyPath = basePath + "scrcpy.exe";
    private java.util.Set<String> activeDevices = new java.util.HashSet<>();
//===================================================================================================
    private ScrcpyService scrcpyService;
    private WindowManager windowManager;
    private ExecutorService executor = Executors.newFixedThreadPool(10);
// index grid
    private int currentIndex = 0;

// auto detect device
    private java.util.Set<String> lastDevices = new java.util.HashSet<>();

    public MainDashboard() {
        initComponents();

        scrcpyService = new ScrcpyService(scrcpyPath);
        windowManager = new WindowManager();
        startAutoDeviceWatcher();
//=================================================================================================
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.getContentPane().setLayout(new BorderLayout());
        this.getContentPane().add(panelDevices, BorderLayout.WEST);
        this.getContentPane().add(panelLayar, BorderLayout.CENTER);
        panelDevices.setBackground(new Color(245, 246, 250));
        //panelLayar.setLayout(new GridLayout(0, COLS, GAP, GAP));
        panelLayar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
panelLayar.setLayout(new BorderLayout(10, 10));
        setupStyle();
        refreshDeviceList();

        // 🔥 sync saat resize / pindah
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                updateScrcpyPositions();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                updateScrcpyPositions();
            }
        });

        // 🔥 sync saat fullscreen / maximize
        this.addWindowStateListener(e -> {
            executor.submit(() -> {
                try {
                    Thread.sleep(300);
                    SwingUtilities.invokeLater(() -> updateScrcpyPositions());
                } catch (Exception ex) {
                }
            });
        });

        // 🔥 AUTO SYNC LOOP (PALING PENTING)
        new javax.swing.Timer(1000, e -> updateScrcpyPositions()).start();

        // 🔥 HANDLE CLOSE (rapi, bukan taskkill brutal)
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeAllScrcpy();
                executor.shutdownNow();
            }
        });
    }

    private void runAdbCommand(String command) {
        try {
            Runtime.getRuntime().exec(command);
        } catch (Exception ex) {
            tulisLog("Gagal: " + ex.getMessage());
        }
    }

    private Rectangle getGridBounds(int index) {
        Dimension d = panelLayar.getSize();

        int panelWidth = d.width;
        int panelHeight = d.height;

        if (panelWidth <= 100 || panelHeight <= 100) {
            return new Rectangle(0, 0, 300, 600);
        }

        int devicesCount = Math.max(1, Math.max(windowManager.getAll().size(), activeDevices.size()));

        int cellWidth = (panelWidth - ((COLS - 1) * GAP)) / COLS;
        int rowCount = (int) Math.ceil((double) devicesCount / COLS);
        int cellHeight = (panelHeight - ((rowCount - 1) * GAP)) / rowCount;

        int col = index % COLS;
        int row = index / COLS;

        return new Rectangle(
                col * (cellWidth + GAP),
                row * (cellHeight + GAP),
                cellWidth,
                cellHeight
        );
    }

    private void setupStyle() {
        txtLog.setEditable(false);
        txtLog.setBackground(Color.BLACK);
        txtLog.setForeground(new Color(50, 255, 50));
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 10));
    }

    // ================= [ CORE MIRRORING ] =================
    private void startScrcpyMirror(String deviceID) {
        if (deviceID == null) {
            return;
        }

        int index;
        synchronized (this) {
            index = currentIndex++;
        }

        executor.submit(() -> {
            try {
                Point p = panelLayar.getLocationOnScreen();
                Rectangle r = getGridBounds(index);

                int padding = 10;

                int targetW = r.width - (padding * 2);
                int targetH = r.height - (padding * 2);

                double ratio = 9.0 / 18.0;

                int w = targetW;
                int h = (int) (w / ratio);

                if (h > targetH) {
                    h = targetH;
                    w = (int) (h * ratio);
                }

                int x = p.x + r.x + (r.width - w) / 2 + OFFSET_X;
                int y = p.y + r.y + (r.height - h) / 2 + OFFSET_Y;

                // 🔥 PANGGIL SERVICE
                scrcpyService.start(deviceID, x, y, w, h);

                // 🔥 REGISTER WINDOW
                windowManager.register(deviceID);

            } catch (Exception e) {
                tulisLog("Error: " + e.getMessage());
            }
        });
    }

    private void updateScrcpyPositions() {
        if (!panelLayar.isShowing()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {

                Point p = panelLayar.getLocationOnScreen();

                List<String> keys = new ArrayList<>(windowManager.getAll().keySet());
                java.util.Collections.sort(keys);

                for (int i = 0; i < keys.size(); i++) {

                    String deviceID = keys.get(i);

                    Rectangle r = getGridBounds(i);

                    int padding = 10;

                    int targetW = r.width - (padding * 2);
                    int targetH = r.height - (padding * 2);

                    double ratio = 9.0 / 18.0;

                    int w = targetW;
                    int h = (int) (w / ratio);

                    if (h > targetH) {
                        h = targetH;
                        w = (int) (h * ratio);
                    }

                    int x = p.x + r.x + (r.width - w) / 2 + OFFSET_X;
                    int y = p.y + r.y + (r.height - h) / 2 + OFFSET_Y;

                    // 🔥 INI KUNCI NYA
                    windowManager.move(deviceID, x, y, w, h);
                }

            } catch (Exception ex) {
                // amanin saat resize/fullscreen
            }
        });
    }

    private void closeAllScrcpy() {
        scrcpyService.stopAll();
    }

    // ================= [ DEVICE LOGIC ] =================
    private void refreshDeviceList() {
        DefaultListModel<String> model = new DefaultListModel<>();
        List<String> devices = getConnectedDevices();

        for (String id : devices) {
            model.addElement(id);
        }

        jListDevices.setModel(model);

        // 🔥 RESET GRID
        currentIndex = 0;
        activeDevices.clear();

        // 🔥 KOSONGKAN PANEL DULU
        panelLayar.removeAll();

        // 🔥 TAMBAH PLACEHOLDER (BIAR KELIHATAN GRID)
        int totalSlot = Math.max(devices.size(), 3); // minimal 3 slot

        for (int i = 0; i < totalSlot; i++) {
            JPanel dummy = new JPanel();
            dummy.setBackground(Color.BLACK);
            dummy.setBorder(BorderFactory.createLineBorder(Color.WHITE, 0));
            panelLayar.add(dummy);
        }

        panelLayar.revalidate();
        panelLayar.repaint();

        tulisLog("Daftar perangkat diperbarui (" + devices.size() + " ditemukan).");
    }

    private List<String> getConnectedDevices() {
        List<String> devices = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(adbPath + " devices");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith("device")) {
                    devices.add(line.split("\t")[0]);
                }
            }
        } catch (Exception e) {
            tulisLog("ADB Error: " + e.getMessage());
        }
        return devices;
    }

    // ================= [ ACTION LISTENERS ] =================
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jOptionPane1 = new javax.swing.JOptionPane();
        panelDevices = new javax.swing.JPanel();
        spLog = new javax.swing.JScrollPane();
        txtLog = new javax.swing.JTextArea();
        spDevices = new javax.swing.JScrollPane();
        jListDevices = new javax.swing.JList<>();
        btnRefresh = new javax.swing.JButton();
        btnConnectWiFi = new javax.swing.JButton();
        btnConnectWiFi.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/wifi_add.svg"));
        btnUSB = new javax.swing.JButton();
        chkSync = new javax.swing.JCheckBox();
        btnInstallAPK = new javax.swing.JButton();
        btnScreenshotAll = new javax.swing.JButton();
        btnRebootAll = new javax.swing.JButton();
        txtInputMasal = new javax.swing.JTextField();
        btnSendText = new javax.swing.JButton();
        btnConnect = new javax.swing.JButton();
        btnBackAll = new javax.swing.JButton();
        btnBackAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/backspace.svg"));
        btnRecentAll = new javax.swing.JButton();
        btnRecentAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/menu.svg"));
        btnHomeAll = new javax.swing.JButton();
        btnHomeAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/home.svg"));
        panelLayar = new javax.swing.JPanel();
        jOptionPane1.getAccessibleContext().setAccessibleParent(this);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setName("MainFrame"); // NOI18N

        panelDevices.setMinimumSize(new java.awt.Dimension(220, 0));
        panelDevices.setPreferredSize(new java.awt.Dimension(280, 600));

        spLog.setFont(new java.awt.Font("Segoe UI", 0, 8)); // NOI18N

        txtLog.setColumns(20);
        txtLog.setFont(new java.awt.Font("Segoe UI", 0, 8)); // NOI18N
        txtLog.setForeground(new java.awt.Color(255, 255, 255));
        txtLog.setRows(5);
        spLog.setViewportView(txtLog);

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

        btnConnectWiFi.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/wifi_add.svg"));
        btnConnectWiFi.setText("Wi-Fi Debug");
        btnConnectWiFi.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnConnectWiFi.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnConnectWiFi.addActionListener(this::btnConnectWiFiActionPerformed);

        btnUSB.setText("USB Debug");
        btnUSB.addActionListener(this::btnUSBActionPerformed);

        chkSync.setText("Sync All Devices");

        btnInstallAPK.setText("Install APK");
        btnInstallAPK.addActionListener(this::btnInstallAPKActionPerformed);

        btnScreenshotAll.setText("Screenshot All");
        btnScreenshotAll.addActionListener(this::btnScreenshotAllActionPerformed);

        btnRebootAll.setText("Reboot All");
        btnRebootAll.addActionListener(this::btnRebootAllActionPerformed);

        btnSendText.setText("Send All");
        btnSendText.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnSendText.addActionListener(this::btnSendTextActionPerformed);

        btnConnect.setText("Connect");
        btnConnect.addActionListener(this::btnConnectActionPerformed);

        btnBackAll.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/backspace.svg")
        );
        btnBackAll.setText("Back");
        btnBackAll.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnBackAll.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnBackAll.addActionListener(this::btnBackAllActionPerformed);

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

        javax.swing.GroupLayout panelDevicesLayout = new javax.swing.GroupLayout(panelDevices);
        panelDevices.setLayout(panelDevicesLayout);
        panelDevicesLayout.setHorizontalGroup(
            panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelDevicesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panelDevicesLayout.createSequentialGroup()
                        .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(txtInputMasal, javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, panelDevicesLayout.createSequentialGroup()
                                .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(btnConnect, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(spDevices, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 137, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(btnUSB, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(btnRefresh, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(btnConnectWiFi, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(panelDevicesLayout.createSequentialGroup()
                        .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(btnScreenshotAll)
                                .addGroup(panelDevicesLayout.createSequentialGroup()
                                    .addComponent(btnRebootAll)
                                    .addGap(18, 18, 18)
                                    .addComponent(btnInstallAPK)))
                            .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(chkSync)
                                .addGroup(panelDevicesLayout.createSequentialGroup()
                                    .addComponent(btnRecentAll)
                                    .addGap(18, 18, 18)
                                    .addComponent(btnHomeAll)
                                    .addGap(18, 18, 18)
                                    .addComponent(btnBackAll))
                                .addComponent(btnSendText)))
                        .addContainerGap(22, Short.MAX_VALUE))))
            .addComponent(spLog)
        );
        panelDevicesLayout.setVerticalGroup(
            panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelDevicesLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(spLog, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 56, Short.MAX_VALUE)
                .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(panelDevicesLayout.createSequentialGroup()
                        .addComponent(btnUSB)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRefresh))
                    .addComponent(spDevices, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnConnect)
                    .addComponent(btnConnectWiFi))
                .addGap(48, 48, 48)
                .addComponent(chkSync)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtInputMasal, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnSendText)
                .addGap(34, 34, 34)
                .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnRebootAll)
                    .addComponent(btnInstallAPK))
                .addGap(18, 18, 18)
                .addComponent(btnScreenshotAll)
                .addGap(104, 104, 104)
                .addGroup(panelDevicesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnBackAll)
                    .addComponent(btnHomeAll)
                    .addComponent(btnRecentAll))
                .addGap(22, 22, 22))
        );

        getContentPane().add(panelDevices, java.awt.BorderLayout.WEST);

        panelLayar.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout panelLayarLayout = new javax.swing.GroupLayout(panelLayar);
        panelLayar.setLayout(panelLayarLayout);
        panelLayarLayout.setHorizontalGroup(
            panelLayarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 720, Short.MAX_VALUE)
        );
        panelLayarLayout.setVerticalGroup(
            panelLayarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 600, Short.MAX_VALUE)
        );

        getContentPane().add(panelLayar, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnRecentAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRecentAllActionPerformed
        runAdbCommand(adbPath + " -s " + jListDevices.getSelectedValue() + " shell input keyevent 187");
    }//GEN-LAST:event_btnRecentAllActionPerformed

    private void btnBackAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBackAllActionPerformed
        runAdbCommand(adbPath + " -s " + jListDevices.getSelectedValue() + " shell input keyevent 4");
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
            tulisLog("Silakan pilih perangkat!");
            return;
        }

        startScrcpyMirror(selectedID);
    }//GEN-LAST:event_btnConnectActionPerformed

    private void btnRebootAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRebootAllActionPerformed

    }//GEN-LAST:event_btnRebootAllActionPerformed

    private void btnScreenshotAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnScreenshotAllActionPerformed
        List<String> devices = getConnectedDevices();
        if (devices.isEmpty()) {
            tulisLog("Gagal: Tidak ada HP terkoneksi.");
            return;
        }

        // 1. Buat folder penyimpanan (Misal di Documents/Mirror_Screenshots)
        String folderPath = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "Mirror_Screenshots";
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        tulisLog("Memulai Screenshot Masal...");
        tulisLog("Folder simpan: " + folderPath);

        // 2. Loop semua HP
        for (String id : devices) {
            executor.submit(() -> {
                try {
                    // Buat nama file unik (ID_HP + Jam_Menit_Detik)
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    // Bersihkan ID dari karakter yang dilarang di nama file Windows (seperti : atau .)
                    String cleanID = id.replace(":", "_").replace(".", "_");
                    String fileName = "SC_" + cleanID + "_" + timestamp + ".png";
                    String fullPath = folderPath + File.separator + fileName;

                    // 3. Eksekusi perintah ADB Screenshot
                    // -p artinya output dalam format PNG
                    ProcessBuilder pb = new ProcessBuilder(adbPath, "-s", id, "exec-out", "screencap", "-p");
                    Process p = pb.start();

                    // 4. Simpan output stream langsung menjadi file di PC
                    try (java.io.InputStream is = p.getInputStream(); java.io.FileOutputStream fos = new java.io.FileOutputStream(fullPath)) {

                        byte[] buffer = new byte[1024];
                        int n;
                        while ((n = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, n);
                        }
                    }

                    tulisLog("[" + id + "] Screenshot Berhasil: " + fileName);

                } catch (Exception e) {
                    tulisLog("[" + id + "] Gagal Screenshot: " + e.getMessage());
                }

            });
        }
        tulisLog("Screenshot sedang diproses... cek folder.");
    }//GEN-LAST:event_btnScreenshotAllActionPerformed

    private void btnInstallAPKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInstallAPKActionPerformed
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Android Package (.apk)", "apk");
        chooser.setFileFilter(filter);

        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File apkFile = chooser.getSelectedFile();
            String apkPath = apkFile.getAbsolutePath();

            List<String> devices = getConnectedDevices();
            if (devices.isEmpty()) {
                tulisLog("Gagal: Tidak ada HP yang terhubung.");
                return;
            }

            tulisLog("Memulai instalasi masal: " + apkFile.getName());

            for (String id : devices) {
                executor.submit(() -> {
                    try {
                        // A. Bypass Verifikasi (Seringkali harus dilakukan tepat sebelum install)
                        runAdbCommand(adbPath + " -s " + id + " shell settings put global verifier_verify_adb_installs 0");
                        runAdbCommand(adbPath + " -s " + id + " shell settings put global package_verifier_enable 0");

                        tulisLog("[" + id + "] Sedang menginstal...");

                        // B. Gunakan Array String untuk menghindari error spasi pada path
                        // Parameter -r ditambahkan agar bisa menimpa aplikasi lama (reinstall)
                        String[] cmd = {adbPath, "-s", id, "install", "-r", "-g", apkPath};
                        Process p = Runtime.getRuntime().exec(cmd);

                        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                        String line;
                        boolean sukses = false;
                        while ((line = r.readLine()) != null) {
                            tulisLog("[" + id + " Output]: " + line); // Opsional: Pantau log asli ADB
                            if (line.toLowerCase().contains("success")) {
                                sukses = true;
                            }
                        }

                        if (sukses) {
                            tulisLog("[" + id + "] BERHASIL diinstal.");
                        } else {
                            tulisLog("[" + id + "] GAGAL: Pastikan 'Install via USB' aktif di HP.");
                        }

                    } catch (Exception e) {
                        tulisLog("[" + id + "] Error: " + e.getMessage());
                    }
                });
            }
            tulisLog("Instalasi berjalan di background...");
        }
    }//GEN-LAST:event_btnInstallAPKActionPerformed

    private void btnUSBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUSBActionPerformed
        // Jalankan di Thread terpisah agar GUI tidak beku
        new Thread(() -> {
            try {
                tulisLog("Memulai pemindaian jaringan...");

                // 1. Dapatkan Prefix IP Laptop (Contoh: 192.168.1.)
                String myIp = java.net.InetAddress.getLocalHost().getHostAddress();
                String prefix = myIp.substring(0, myIp.lastIndexOf(".") + 1);
                String adbPath = basePath + "adb.exe";

                tulisLog("Segmen jaringan terdeteksi: " + prefix + "0/24");

                // 2. Gunakan Pool Thread (50 thread sekaligus) agar super cepat
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(50);

                for (int i = 1; i < 255; i++) {
                    final String testIp = prefix + i;

                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                java.net.InetAddress addr = java.net.InetAddress.getByName(testIp);
                                // Ping dengan timeout lebih santai (800ms)
                                if (addr.isReachable(800)) {

                                    // Gunakan timeout pada level command line agar tidak gantung
                                    Process p = Runtime.getRuntime().exec(adbPath + " connect " + testIp + ":5555");

                                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                                    String res = r.readLine();

                                    if (res != null && (res.contains("connected") || res.contains("already connected"))) {
                                        tulisLog("DITEMUKAN: " + testIp);
                                        SwingUtilities.invokeLater(() -> refreshDeviceList());
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }
                    });
                }

                executor.shutdown();
                // Tunggu sampai semua thread selesai (maksimal 15 detik)
                if (executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)) {
                    tulisLog("Pemindaian selesai.");
                }

            } catch (Exception e) {
                tulisLog("Error Scan: " + e.getMessage());
            }
        }).start();
    }

    private void tulisLog(String pesan) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append("> " + pesan + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());

        });
    }//GEN-LAST:event_btnUSBActionPerformed

    private void btnConnectWiFiActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectWiFiActionPerformed
        String input = txtInputMasal.getText().trim();

        if (input.isEmpty()) {
            tulisLog("Masukkan IP (bisa banyak, pisah koma)");
            return;
        }

        String[] ips = input.split(",");

        for (String ip : ips) {

            ip = ip.trim();

            if (!ip.contains(":")) {
                ip += ":5555";
            }

            String finalIp = ip;

            executor.submit(() -> connectWifi(finalIp));
        }
    }//GEN-LAST:event_btnConnectWiFiActionPerformed

    private void connectWifi(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            tulisLog("IP kosong.");
            return;
        }

        executor.submit(() -> {
            try {
                Process p = Runtime.getRuntime().exec(adbPath + " connect " + ipAddress);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream())
                );

                String response = reader.readLine();

                if (response != null && response.contains("connected")) {
                    tulisLog("Berhasil connect: " + ipAddress);
                    refreshDeviceList();
                } else {
                    tulisLog("Gagal connect: " + response);
                }

            } catch (Exception ex) {
                tulisLog("Error: " + ex.getMessage());
            }
        });
    }

    private void btnRefreshActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshActionPerformed
        executor.submit(() -> {
            try {
                closeAllScrcpy();
                Thread.sleep(800);
                SwingUtilities.invokeLater(this::refreshDeviceList);
            } catch (Exception e) {
            }
        });
    }//GEN-LAST:event_btnRefreshActionPerformed

    private void btnHomeAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnHomeAllActionPerformed
        if (chkSync.isSelected()) {
            // Jika centang Sync nyala, kirim ke SEMUA perangkat di list
            List<String> devices = getConnectedDevices();
            for (String id : devices) {
                executor.submit(() -> runAdbCommand(adbPath + " -s " + id + " shell input keyevent 3"));
            }
            tulisLog("Sync Home ke " + devices.size() + " HP.");
        } else {
            // Jika tidak, hanya kirim ke yang dipilih di list
            String selected = jListDevices.getSelectedValue();
            if (selected != null) {
                runAdbCommand(adbPath + " -s " + selected + " shell input keyevent 3");
            }
        }
    }//GEN-LAST:event_btnHomeAllActionPerformed

    private void btnSendTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSendTextActionPerformed
        String teks = txtInputMasal.getText();

        // Validasi jika teks kosong
        if (teks.trim().isEmpty()) {
            tulisLog("Gagal: Teks input kosong.");
            return;
        }

        // Karakter spasi harus diganti dengan %s agar ADB bisa membacanya
        String teksAdb = teks.replace(" ", "%s");

        List<String> devices = getConnectedDevices();
        if (devices.isEmpty()) {
            return;
        }

        tulisLog("Mengirim teks: '" + teks + "' ke semua HP...");

        for (String id : devices) {
            executor.submit(() -> {
                try {
                    // Perintah 'input text' di Android
                    runAdbCommand(adbPath + " -s " + id + " shell input text " + teksAdb);
                    tulisLog("[" + id + "] Teks terkirim.");
                } catch (Exception e) {
                    tulisLog("[" + id + "] Error kirim teks: " + e.getMessage());
                }
            });
        }

        // Kosongkan kotak input setelah kirim agar bersih
        txtInputMasal.setText("");
    }//GEN-LAST:event_btnSendTextActionPerformed

    private void startAutoDeviceWatcher() {

        executor.submit(() -> {

            while (true) {
                try {

                    List<String> currentDevices = getConnectedDevices();
                    java.util.Set<String> currentSet = new java.util.HashSet<>(currentDevices);

                    if (!currentSet.equals(lastDevices)) {

                        tulisLog("Perubahan device terdeteksi...");
                        lastDevices = currentSet;

                        SwingUtilities.invokeLater(() -> {
                            refreshDeviceList();
                        });
                    }

                    Thread.sleep(2000);

                } catch (Exception e) {
                    tulisLog("Watcher error: " + e.getMessage());
                }
            }

        });
    }

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
    private javax.swing.JButton btnHomeAll;
    private javax.swing.JButton btnInstallAPK;
    private javax.swing.JButton btnRebootAll;
    private javax.swing.JButton btnRecentAll;
    private javax.swing.JButton btnRefresh;
    private javax.swing.JButton btnScreenshotAll;
    private javax.swing.JButton btnSendText;
    private javax.swing.JButton btnUSB;
    private javax.swing.JCheckBox chkSync;
    private javax.swing.JList<String> jListDevices;
    private javax.swing.JOptionPane jOptionPane1;
    private javax.swing.JPanel panelDevices;
    private javax.swing.JPanel panelLayar;
    private javax.swing.JScrollPane spDevices;
    private javax.swing.JScrollPane spLog;
    private javax.swing.JTextField txtInputMasal;
    private javax.swing.JTextArea txtLog;
    // End of variables declaration//GEN-END:variables
}
