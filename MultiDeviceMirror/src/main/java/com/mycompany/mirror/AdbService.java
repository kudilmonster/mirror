package com.mycompany.mirror;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import javax.swing.SwingUtilities;

public class AdbService {

    private final MainDashboard dashboard;
    private final String adb;
    private final ExecutorService executor;

    public AdbService(MainDashboard dashboard, String adbExecutable, ExecutorService executor) {
        this.dashboard = dashboard;
        this.adb = adbExecutable;
        this.executor = executor;
    }

    // ================= KONTROL DASAR =================
    public void runCommand(String... cmd) {
        executor.submit(() -> {
            String output = CommandExecutor.executeWithTimeout(5000, cmd);
            if (!output.trim().isEmpty()) {
                dashboard.log(output.trim());
            }
        });
    }

    public List<String> getConnectedDevices() {
        List<String> devices = new ArrayList<>();
        String output = CommandExecutor.executeWithTimeout(3000, adb, "devices");

        if (output.contains("ERROR") || output.contains("TIMEOUT")) {
            dashboard.log("ADB Scan Error: " + output);
            return devices;
        }

        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.endsWith("device")) {
                devices.add(line.split("\t")[0]);
            }
        }
        return devices;
    }

    // ================= KONEKSI JARINGAN =================
    public void connectWifi(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return;
        }
        executor.submit(() -> {
            String output = CommandExecutor.executeWithTimeout(5000, adb, "connect", ipAddress);
            if (output.contains("connected")) {
                dashboard.log("Berhasil connect: " + ipAddress);
                // Ambil list terbaru, lalu kirim ke dashboard
                List<String> updatedDevices = getConnectedDevices();
                SwingUtilities.invokeLater(() -> dashboard.refreshDeviceList(updatedDevices));
            } else {
                dashboard.log("Gagal connect: " + output.trim());
            }
        });
    }

    public void enableTcpIp(String deviceId) {
        dashboard.log("Membuka port 5555 untuk: " + deviceId + "...");
        executor.submit(() -> {
            String output = CommandExecutor.executeWithTimeout(5000, adb, "-s", deviceId, "tcpip", "5555");
            if (output.contains("restarting in TCP mode") || output.trim().isEmpty()) {
                dashboard.log("✅ Port 5555 berhasil dibuka pada HP " + deviceId + "!");
            } else {
                dashboard.log("Gagal: " + output.trim());
            }
        });
    }

    public void scanNetworkForDevices() {
        executor.submit(() -> {
            try {
                dashboard.log("Memulai pemindaian jaringan...");
                String myIp = java.net.InetAddress.getLocalHost().getHostAddress();
                String prefix = myIp.substring(0, myIp.lastIndexOf(".") + 1);

                ExecutorService scanPool = java.util.concurrent.Executors.newFixedThreadPool(50);
                for (int i = 1; i < 255; i++) {
                    final String testIp = prefix + i;
                    scanPool.execute(() -> {
                        try {
                            if (java.net.InetAddress.getByName(testIp).isReachable(800)) {
                                String res = CommandExecutor.executeWithTimeout(2000, adb, "connect", testIp + ":5555");
                                if (res != null && (res.contains("connected") || res.contains("already connected"))) {
                                    dashboard.log("DITEMUKAN: " + testIp);
                                    // Ambil list terbaru, lalu kirim ke dashboard
                                    List<String> updatedDevices = getConnectedDevices();
                                    SwingUtilities.invokeLater(() -> dashboard.refreshDeviceList(updatedDevices));
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }
                scanPool.shutdown();
                if (scanPool.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)) {
                    dashboard.log("Pemindaian jaringan selesai.");
                }
            } catch (Exception e) {
                dashboard.log("Error Scan: " + e.getMessage());
            }
        });
    }

    // ================= FUNGSI MASSAL (BULK) =================
    public void installApkMassal(String apkPath, List<String> devices) {
        dashboard.log("Memulai instalasi masal APK...");
        for (String id : devices) {
            executor.submit(() -> {
                try {
                    runCommand(adb, "-s", id, "shell", "settings", "put", "global", "verifier_verify_adb_installs", "0");
                    runCommand(adb, "-s", id, "shell", "settings", "put", "global", "package_verifier_enable", "0");
                    dashboard.log("[" + id + "] Sedang menginstal...");

                    // Gunakan timeout 60 detik karena install APK butuh waktu lama
                    String output = CommandExecutor.executeWithTimeout(60000, adb, "-s", id, "install", "-r", "-g", apkPath);
                    if (output.toLowerCase().contains("success")) {
                        dashboard.log("[" + id + "] BERHASIL diinstal.");
                    } else {
                        dashboard.log("[" + id + "] GAGAL: " + output.trim());
                    }
                } catch (Exception e) {
                    dashboard.log("[" + id + "] Error: " + e.getMessage());
                }
            });
        }
    }

    public void screenshotMassal(String folderPath, List<String> devices) {
        dashboard.log("Memulai Screenshot Masal...");
        for (String id : devices) {
            executor.submit(() -> {
                try {
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String fileName = "SC_" + id.replace(":", "_").replace(".", "_") + "_" + timestamp + ".png";
                    String fullPath = folderPath + File.separator + fileName;

                    Process p = new ProcessBuilder(adb, "-s", id, "exec-out", "screencap", "-p").start();
                    try (InputStream is = p.getInputStream(); FileOutputStream fos = new FileOutputStream(fullPath)) {
                        byte[] buffer = new byte[1024];
                        int n;
                        while ((n = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, n);
                        }
                    }
                    dashboard.log("[" + id + "] Tersimpan: " + fileName);
                } catch (Exception e) {
                    dashboard.log("[" + id + "] Gagal Screenshot: " + e.getMessage());
                }
            });
        }
    }

    public void rebootMassal(List<String> devices) {
        dashboard.log("Memulai proses Reboot...");
        for (String id : devices) {
            executor.submit(() -> runCommand(adb, "-s", id, "reboot"));
        }
    }

    public void sendTextMassal(String text, List<String> devices) {
        String teksAdb = text.replace(" ", "%s");
        for (String id : devices) {
            executor.submit(() -> runCommand(adb, "-s", id, "shell", "input", "text", teksAdb));
        }
        dashboard.log("Teks terkirim ke semua HP.");
    }

    public void sendKeyEvent(String keyCode, List<String> devices) {
        for (String id : devices) {
            executor.submit(() -> runCommand(adb, "-s", id, "shell", "input", "keyevent", keyCode));
        }
    }
}
