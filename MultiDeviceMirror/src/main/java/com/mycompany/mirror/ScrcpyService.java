package com.mycompany.mirror;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

public class ScrcpyService {

    private final String executable;
    private final ExecutorService executor;
    private final MainDashboard dashboard;
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

    public ScrcpyService(String executable, ExecutorService executor, MainDashboard dashboard) {
        this.executable = executable;
        this.executor = executor;
        this.dashboard = dashboard;
    }

    public void start(String deviceId, String windowTitle, boolean isRecording) {
        executor.submit(() -> {
            try {
                int randomPort = 20000 + new Random().nextInt(5000);
                
                // Siapkan folder rekaman
                File recordDir = new File("recordings");
                if (!recordDir.exists()) recordDir.mkdirs();

                List<String> cmd = new ArrayList<>(Arrays.asList(
                    executable,
                    "-s", deviceId,
                    "--window-title", windowTitle,
                    "--always-on-top",
                    "--no-audio-playback",
                    "--record=file.mkv",
                    "--port", String.valueOf(randomPort),
                    "--max-size", "480",
                    "--video-bit-rate", "1M", // 1 Mbps sudah cukup jernih
                    "--max-fps", "15" // 15 FPS untuk PC yang lebih berterima kasih
                ));

                // 🔥 Jika fitur record dicentang
                if (isRecording) {
                    // 🔥 1. KOREKSI PENANGGALAN: Gunakan format yang benar
                    String safeDeviceId = deviceId.replace(":", "_"); 
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
                    String dateString = now.format(dtf);
                    
                    String fileName = "REC_" + safeDeviceId + "_" + dateString + ".mkv";
                    File recordFile = new File(recordDir, fileName);
                    cmd.add("--record");
                    cmd.add(recordFile.getAbsolutePath());
                    dashboard.log("🎥 Recording Aktif: " + fileName);
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                Process p = pb.start();
                runningProcesses.put(windowTitle, p);

                dashboard.log("Memulai instance: " + windowTitle + " (Mode Hemat)");

            } catch (Exception e) {
                // 🔥 2. TAMBAHKAN LOG KE CATCH BLOCK
                dashboard.log("Gagal menjalankan Scrcpy: " + e.getMessage());
            }
        });
    }

    public void stopAll() {
        // Matikan semua proses Scrcpy dengan aman
        runningProcesses.forEach((title, process) -> {
            if (process != null && process.isAlive()) {
                process.destroyForcibly(); 
            }
        });
        runningProcesses.clear();

        // 🔥 Perintahkan Dashboard untuk menghapus komponen Grid dari memori
        if (dashboard != null) {
            dashboard.clearGrid();
        }
    }

    // Tambahkan method ini di bawah stopAll()
    public void stop(String windowTitle) {
        Process p = runningProcesses.remove(windowTitle);
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            dashboard.log("Berhasil mematikan: " + windowTitle);
        }
    }
    
    // ==========================================================
    // JNA EMBEDDING LOGIC (PERBAIKAN)
    // ==========================================================
    public void embed(String windowTitle, java.awt.Canvas canvas, MainDashboard dashboard, JSpinner spinX, JSpinner spinY) {
        // 🔥 3. GANTI 'new Thread' dengan 'executor.submit'
        executor.submit(() -> {
            com.sun.jna.platform.win32.WinDef.HWND foundHwnd = null;
            int retries = 0;

            // Polling untuk mencari window scrcpy (timeout maks 20 * 600ms = 12 detik)
            while (foundHwnd == null && retries < 20) {
                try {
                    Thread.sleep(600); // Polling interval
                    foundHwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, windowTitle);
                    retries++;
                } catch (InterruptedException e) {}
            }

            if (foundHwnd != null) {
                // 🔥 4. HAPUS SLEEP 1 DETIK YANG TIDAK PERLU INI
                // try { Thread.sleep(1000); } catch (InterruptedException e) {}

                final com.sun.jna.platform.win32.WinDef.HWND finalHwnd = foundHwnd;
                com.sun.jna.platform.win32.WinDef.HWND canvasHwnd = new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(canvas));

                int style = com.sun.jna.platform.win32.User32.INSTANCE.GetWindowLong(finalHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE);
                style = style & ~com.sun.jna.platform.win32.WinUser.WS_POPUP & ~com.sun.jna.platform.win32.WinUser.WS_CAPTION & ~com.sun.jna.platform.win32.WinUser.WS_THICKFRAME;
                style = style | com.sun.jna.platform.win32.WinUser.WS_CHILD | com.sun.jna.platform.win32.WinUser.WS_VISIBLE;
                
                com.sun.jna.platform.win32.User32.INSTANCE.SetWindowLong(finalHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE, style);
                com.sun.jna.platform.win32.User32.INSTANCE.SetParent(finalHwnd, canvasHwnd);
                com.sun.jna.platform.win32.User32.INSTANCE.ShowWindow(finalHwnd, com.sun.jna.platform.win32.WinUser.SW_SHOW);

                // Fungsi Resize Otomatis
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

                // Hapus listener lama jika ada (mencegah leak)
                for (java.awt.event.ComponentListener cl : canvas.getComponentListeners()) {
                    canvas.removeComponentListener(cl);
                }

                canvas.addComponentListener(new java.awt.event.ComponentAdapter() {
                    @Override public void componentResized(java.awt.event.ComponentEvent e) { doResize.run(); }
                });

                // Jalankan resize awal di Event Dispatch Thread
                SwingUtilities.invokeLater(doResize);
                dashboard.log("✅ Berhasil embed ke Slot: " + windowTitle);
            } else {
                dashboard.log("❌ Gagal: Jendela Scrcpy " + windowTitle + " tidak ditemukan.");
            }
        });
    }
}