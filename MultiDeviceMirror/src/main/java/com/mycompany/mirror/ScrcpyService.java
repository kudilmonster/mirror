package com.mycompany.mirror;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

public class ScrcpyService {

    private final String executable; // Path ke scrcpy.exe
    private final ExecutorService executor; // Untuk menjalankan di background
    private final MainDashboard dashboard; // Untuk kirim log ke UI
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

    // 🔥 Constructor baru: Menerima data dari MainDashboard
    public ScrcpyService(String executable, ExecutorService executor, MainDashboard dashboard) {
        this.executable = executable;
        this.executor = executor;
        this.dashboard = dashboard;
    }

public void start(String deviceId, String windowTitle) {
        executor.submit(() -> {
            try {
                int randomPort = 20000 + new Random().nextInt(5000);

                // Gunakan parameter yang paling dasar tapi efektif
ProcessBuilder pb = new ProcessBuilder(
    executable,
    "-s", deviceId,
    "--window-title", windowTitle,
    "--always-on-top",
    "--no-audio",
    "--port", String.valueOf(randomPort),
    
    // 🔥 SETELAN DIET EKSTREM (SUPER RINGAN)
    "--max-size", "480",              // 1. Turunkan ke 480p (Sangat direkomendasikan untuk layar duplikat yang ukurannya kecil di UI)
    "--video-bit-rate", "1M",         // 2. 1 Mbps sudah lebih dari cukup untuk 480p
    "--max-fps", "15",                // 3. 15-20 FPS. Agak kaku, tapi PC Anda akan sangat berterima kasih.
    "--no-key-repeat",                // 4. Mencegah spam input
    "--no-clipboard-autosync"        // 5. Matikan auto-sync copy-paste antara HP dan PC (mengurangi proses background)
    //"--turn-screen-off"               // 6. Matikan layar fisik HP saat di-mirror (Mencegah HP panas/throttling yang bikin lag)
);

                // Hapus baris --encoder karena itu penyebab utamanya
                
                Process p = pb.start();
                runningProcesses.put(windowTitle, p);

                dashboard.log("Memulai instance: " + windowTitle + " (Mode Hemat)");

            } catch (Exception e) {
                dashboard.log("Gagal menjalankan Scrcpy: " + e.getMessage());
            }
        });
    }

    public void stopAll() {
        runningProcesses.values().forEach(Process::destroy);
        runningProcesses.clear();
    }

    // ==========================================================
    // JNA EMBEDDING LOGIC (PINDAHAN DARI MAIN DASHBOARD)
    // ==========================================================
    public void embed(String windowTitle, java.awt.Canvas canvas, MainDashboard dashboard, JSpinner spinX, JSpinner spinY) {
        new Thread(() -> {
            com.sun.jna.platform.win32.WinDef.HWND foundHwnd = null;
            int retries = 0;

            while (foundHwnd == null && retries < 20) {
                try {
                    Thread.sleep(600); // Beri jeda sedikit lebih lama agar OS sempat render
                    foundHwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, windowTitle);
                    retries++;
                } catch (InterruptedException e) {}
            }

            if (foundHwnd != null) {
                try { Thread.sleep(1000); } catch (InterruptedException e) {}

                final com.sun.jna.platform.win32.WinDef.HWND finalHwnd = foundHwnd;
                com.sun.jna.platform.win32.WinDef.HWND canvasHwnd = new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(canvas));

                int style = com.sun.jna.platform.win32.User32.INSTANCE.GetWindowLong(finalHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE);
                style = style & ~com.sun.jna.platform.win32.WinUser.WS_POPUP & ~com.sun.jna.platform.win32.WinUser.WS_CAPTION & ~com.sun.jna.platform.win32.WinUser.WS_THICKFRAME;
                style = style | com.sun.jna.platform.win32.WinUser.WS_CHILD | com.sun.jna.platform.win32.WinUser.WS_VISIBLE;
                
                com.sun.jna.platform.win32.User32.INSTANCE.SetWindowLong(finalHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE, style);
                com.sun.jna.platform.win32.User32.INSTANCE.SetParent(finalHwnd, canvasHwnd);
                com.sun.jna.platform.win32.User32.INSTANCE.ShowWindow(finalHwnd, com.sun.jna.platform.win32.WinUser.SW_SHOW);

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

                        int offX = (int) spinX.getValue();
                        int offY = (int) spinY.getValue();
                        int x = ((canvasW - targetW) / 2) + offX;
                        int y = ((canvasH - targetH) / 2) + offY;

                        com.sun.jna.platform.win32.User32.INSTANCE.SetWindowPos(finalHwnd, null, x, y, targetW, targetH, 0x0040);
                    }
                };

                spinX.addChangeListener(e -> doResize.run());
                spinY.addChangeListener(e -> doResize.run());
                canvas.addComponentListener(new java.awt.event.ComponentAdapter() {
                    @Override public void componentResized(java.awt.event.ComponentEvent e) { doResize.run(); }
                });

                SwingUtilities.invokeLater(doResize);
                dashboard.log("✅ Berhasil embed ke Slot: " + windowTitle);
            } else {
                dashboard.log("❌ Gagal: Jendela Scrcpy " + windowTitle + " tidak ditemukan.");
            }
        }).start();
    }
}