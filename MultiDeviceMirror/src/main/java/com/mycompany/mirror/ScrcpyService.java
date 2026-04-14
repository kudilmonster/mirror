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

    // 🔥 DITAMBAHKAN PARAMETER: boolean mutePlayback
    public void start(String deviceId, String windowTitle, boolean isRecording, boolean mutePlayback) {
        executor.submit(() -> {
            try {
                int randomPort = 20000 + new Random().nextInt(5000);
                
                File recordDir = new File("recordings");
                if (!recordDir.exists()) recordDir.mkdirs();

                List<String> cmd = new ArrayList<>(Arrays.asList(
                    executable,
                    "-s", deviceId,
                    "--window-title", windowTitle,
                    "--always-on-top",
                    "--port", String.valueOf(randomPort),
                    "--max-size", "480",
                    "--video-bit-rate", "1M", 
                    "--max-fps", "15" 
                ));

                // 🔥 LOGIKA TOGGLE AUDIO: Mute PC Playback jika dicentang
                if (mutePlayback) {
                    cmd.add("--no-audio-playback");
                }

                if (isRecording) {
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

                dashboard.log("Memulai instance: " + windowTitle);

            } catch (Exception e) {
                dashboard.log("Gagal menjalankan Scrcpy: " + e.getMessage());
            }
        });
    }

    public void stopAll() {
        runningProcesses.forEach((title, process) -> {
            if (process != null && process.isAlive()) {
                process.destroyForcibly(); 
            }
        });
        runningProcesses.clear();

        if (dashboard != null) {
            dashboard.clearGrid();
        }
    }
    
    public void stop(String windowTitle) {
        Process p = runningProcesses.remove(windowTitle);
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            dashboard.log("Berhasil mematikan: " + windowTitle);
        }
    }

    public void embed(String windowTitle, java.awt.Canvas canvas, MainDashboard dashboard, JSpinner spinX, JSpinner spinY) {
        executor.submit(() -> {
            com.sun.jna.platform.win32.WinDef.HWND foundHwnd = null;
            int retries = 0;

            while (foundHwnd == null && retries < 20) {
                try {
                    Thread.sleep(600); 
                    foundHwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, windowTitle);
                    retries++;
                } catch (InterruptedException e) {}
            }

            if (foundHwnd != null) {
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

                for (java.awt.event.ComponentListener cl : canvas.getComponentListeners()) {
                    canvas.removeComponentListener(cl);
                }

                canvas.addComponentListener(new java.awt.event.ComponentAdapter() {
                    @Override public void componentResized(java.awt.event.ComponentEvent e) { doResize.run(); }
                });

                SwingUtilities.invokeLater(doResize);
                dashboard.log("✅ Berhasil embed ke Slot: " + windowTitle);
            } else {
                dashboard.log("❌ Gagal: Jendela Scrcpy " + windowTitle + " tidak ditemukan.");
            }
        });
    }
}