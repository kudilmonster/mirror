package com.mycompany.mirror;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JSpinner;
import javax.swing.event.ChangeListener;

public class ScrcpyService {

    private final String scrcpyPath;

    // Map untuk melacak proses scrcpy yang sedang berjalan agar bisa dimatikan nanti
    private Map<String, Process> processes = new ConcurrentHashMap<>();

    public ScrcpyService(String scrcpyPath) {
        this.scrcpyPath = scrcpyPath;
    }

    // Parameter x, y, w, h dihapus karena posisi & ukuran sudah di-handle oleh JNA (Canvas)
    public void start(String deviceID, String windowTitle) {
        if (deviceID == null || deviceID.isEmpty()) {
            return;
        }

        if (processes.containsKey(deviceID)) {
            log("Sudah jalan: " + deviceID);
            return;
        }

        // Tidak perlu "new Thread" lagi karena di MainDashboard sudah dibungkus executor.submit()
        try {
            // 🔥 Injeksi setingan JNA & Low Graphics (Bot Farm Mode)
            ProcessBuilder pb = new ProcessBuilder(
                    scrcpyPath,
                    "-s", deviceID,
                    "--window-title", windowTitle, // Judul wajib untuk ditangkap JNA
                    "--window-borderless",
                    "-m", "800", // Resolusi maksimal 800px
                    "-b", "2M", // Bitrate 2 Mbps
                    "--max-fps", "30", // Kunci di 30 FPS
                    "--no-audio" // Matikan suara
            );

            Process p = pb.start();
            processes.put(deviceID, p); // Daftarkan proses ke dalam Map

            log("Start scrcpy: " + deviceID);

        } catch (IOException e) {
            log("Error start: " + e.getMessage());
        }
    }

    public void stopAll() {
        for (Process p : processes.values()) {
            try {
                p.destroy(); // Paksa tutup scrcpy.exe dari background Windows
            } catch (Exception ignored) {
            }
        }
        processes.clear();
        log("Semua proses Scrcpy telah dimatikan.");
    }

    // Fitur tambahan jika nanti kamu mau bikin tombol "Disconnect" per-HP
    public void stopDevice(String deviceID) {
        Process p = processes.remove(deviceID);
        if (p != null) {
            p.destroy();
            log("Stop scrcpy: " + deviceID);
        }
    }

    private void log(String msg) {
        System.out.println("[SCRCPY] " + msg);
    }

// ==========================================================
    // JNA EMBEDDING LOGIC (FIX SPINNER & SCOPE ERROR)
    // ==========================================================
    public void embed(String windowTitle, java.awt.Canvas canvas, MainDashboard dashboard, javax.swing.JSpinner spinX, javax.swing.JSpinner spinY) {
        new Thread(() -> {
            com.sun.jna.platform.win32.WinDef.HWND foundHwnd = null;
            int retries = 0;

            // 1. Cari jendela Scrcpy
            while (foundHwnd == null && retries < 20) {
                try {
                    Thread.sleep(500);
                    foundHwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, windowTitle);
                    retries++;
                } catch (InterruptedException e) {
                }
            }

            if (foundHwnd != null) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                }

                // Deklarasikan sebagai final agar bisa diakses di dalam Lambda/Runnable
                final com.sun.jna.platform.win32.WinDef.HWND finalHwnd = foundHwnd;
                com.sun.jna.platform.win32.WinDef.HWND canvasHwnd = new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(canvas));

                // 2. Setup Style Jendela
                int style = com.sun.jna.platform.win32.User32.INSTANCE.GetWindowLong(finalHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE);
                style = style & ~com.sun.jna.platform.win32.WinUser.WS_POPUP & ~com.sun.jna.platform.win32.WinUser.WS_CAPTION & ~com.sun.jna.platform.win32.WinUser.WS_THICKFRAME;
                style = style | com.sun.jna.platform.win32.WinUser.WS_CHILD | com.sun.jna.platform.win32.WinUser.WS_VISIBLE;

                com.sun.jna.platform.win32.User32.INSTANCE.SetWindowLong(finalHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE, style);
                com.sun.jna.platform.win32.User32.INSTANCE.SetParent(finalHwnd, canvasHwnd);
                com.sun.jna.platform.win32.User32.INSTANCE.ShowWindow(finalHwnd, com.sun.jna.platform.win32.WinUser.SW_SHOW);

                // 3. Logika Resize & Manual Offset
                Runnable doResize = () -> {
                    int canvasW = canvas.getWidth();
                    int canvasH = canvas.getHeight();
                    if (canvasW > 10 && canvasH > 10) {
                        double ratio = 9.0 / 19.5;
                        int targetW = canvasW;
                        int targetH = (int) (targetW / ratio);
                        if (targetH > canvasH) {
                            targetH = canvasH;
                            targetW = (int) (targetH * ratio);
                        }

                        // Ambil nilai dari spinner (Manual Adjustment)
                        int offX = (int) spinX.getValue();
                        int offY = (int) spinY.getValue();

                        int x = ((canvasW - targetW) / 2) + offX;
                        int y = ((canvasH - targetH) / 2) + offY;

                        com.sun.jna.platform.win32.User32.INSTANCE.SetWindowPos(
                                finalHwnd, null, x, y, targetW, targetH,
                                com.sun.jna.platform.win32.WinUser.SWP_NOZORDER | com.sun.jna.platform.win32.WinUser.SWP_SHOWWINDOW
                        );
                    }
                };

                // 🔥 Tambahkan Listener ke Spinner agar posisi update saat angka diklik
                javax.swing.event.ChangeListener spinnerListener = e -> doResize.run();
                spinX.addChangeListener(spinnerListener);
                spinY.addChangeListener(spinnerListener);

                canvas.addComponentListener(new java.awt.event.ComponentAdapter() {
                    @Override
                    public void componentResized(java.awt.event.ComponentEvent e) {
                        doResize.run();
                    }
                });

                // Jalankan resize pertama kali
                javax.swing.SwingUtilities.invokeLater(doResize);

                dashboard.log("Berhasil embed & sinkron spinner: " + windowTitle);
            }
        }).start();
    }
}
