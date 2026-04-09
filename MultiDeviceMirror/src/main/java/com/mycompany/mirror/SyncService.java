package com.mycompany.mirror;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class SyncService {

    private JDialog syncOverlayDialog;
    private final MainDashboard dashboard;
    private final JPanel panelLayar;
    private final JCheckBox chkSync;

    // Constructor menerima lemparan data dari MainDashboard
    public SyncService(MainDashboard dashboard, JPanel panelLayar, JCheckBox chkSync) {
        this.dashboard = dashboard;
        this.panelLayar = panelLayar;
        this.chkSync = chkSync;
        setupSyncOverlay();
    }

    // Masukkan ke dalam ScrcpyService.java
public void embed(String windowTitle, java.awt.Canvas canvas, MainDashboard dashboard) {
    new Thread(() -> {
        com.sun.jna.platform.win32.WinDef.HWND scrcpyHwnd = null;
        int retries = 0;

        while (scrcpyHwnd == null && retries < 20) {
            try {
                Thread.sleep(500);
                scrcpyHwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, windowTitle);
                retries++;
            } catch (InterruptedException e) {}
        }

        if (scrcpyHwnd != null) {
            try { Thread.sleep(1500); } catch (InterruptedException e) {}

            com.sun.jna.platform.win32.WinDef.HWND canvasHwnd = new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(canvas));

            int style = com.sun.jna.platform.win32.User32.INSTANCE.GetWindowLong(scrcpyHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE);
            style = style & ~com.sun.jna.platform.win32.WinUser.WS_POPUP & ~com.sun.jna.platform.win32.WinUser.WS_CAPTION & ~com.sun.jna.platform.win32.WinUser.WS_THICKFRAME;
            style = style | com.sun.jna.platform.win32.WinUser.WS_CHILD | com.sun.jna.platform.win32.WinUser.WS_VISIBLE;
            
            com.sun.jna.platform.win32.User32.INSTANCE.SetWindowLong(scrcpyHwnd, com.sun.jna.platform.win32.WinUser.GWL_STYLE, style);
            com.sun.jna.platform.win32.User32.INSTANCE.SetParent(scrcpyHwnd, canvasHwnd);
            com.sun.jna.platform.win32.User32.INSTANCE.ShowWindow(scrcpyHwnd, com.sun.jna.platform.win32.WinUser.SW_SHOW);

            final com.sun.jna.platform.win32.WinDef.HWND finalHwnd = scrcpyHwnd;
            
            // Fungsi Resize Otomatis
            Runnable doResize = () -> {
                int canvasW = canvas.getWidth();
                int canvasH = canvas.getHeight();
                if (canvasW > 10 && canvasH > 10) {
                    double ratio = 9.5 / 19.5;
                    int targetW = canvasW;
                    int targetH = (int) (targetW / ratio);
                    if (targetH > canvasH) {
                        targetH = canvasH;
                        targetW = (int) (targetH * ratio);
                    }
                    int x = (canvasW - targetW) / 2;
                    int y = (canvasH - targetH) / 2;
                    com.sun.jna.platform.win32.User32.INSTANCE.MoveWindow(finalHwnd, x, y, targetW, targetH, true);
                }
            };

            canvas.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) { doResize.run(); }
            });

            for (int i = 0; i < 5; i++) {
                doResize.run();
                try { Thread.sleep(300); } catch (Exception e) {}
            }
            dashboard.log("Berhasil menanamkan Scrcpy: " + windowTitle);
        }
    }).start();
}

    private void setupSyncOverlay() {
        syncOverlayDialog = new JDialog(dashboard);
        syncOverlayDialog.setUndecorated(true);
        syncOverlayDialog.setBackground(new Color(0, 150, 255, 60)); // Kaca Biru Alpha 60
        syncOverlayDialog.setAlwaysOnTop(true);
        syncOverlayDialog.setFocusableWindowState(false);
        syncOverlayDialog.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

        MouseAdapter ma = new MouseAdapter() {
            private Point startPoint = null;
            private long startTime = 0;

            @Override
            public void mousePressed(MouseEvent e) {
                startPoint = e.getLocationOnScreen();
                startTime = System.currentTimeMillis();
                dashboard.log("⚡ Kaca disentuh!");
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (startPoint == null) return;
                Point endPoint = e.getLocationOnScreen();
                long duration = System.currentTimeMillis() - startTime;

                Canvas targetCanvas = null;
                for (Component cell : panelLayar.getComponents()) {
                    if (cell instanceof JPanel) {
                        JPanel panelCell = (JPanel) cell;
                        if (panelCell.getComponentCount() > 0 && panelCell.getComponent(0) instanceof Canvas) {
                            Canvas c = (Canvas) panelCell.getComponent(0);
                            Rectangle cBounds = new Rectangle(c.getLocationOnScreen(), c.getSize());
                            if (cBounds.contains(startPoint)) {
                                targetCanvas = c;
                                break;
                            }
                        }
                    }
                }

                if (targetCanvas == null) {
                    chkSync.setSelected(false);
                    return;
                }

                Point cLoc = targetCanvas.getLocationOnScreen();
                double ratioStartX = (double) (startPoint.x - cLoc.x) / targetCanvas.getWidth();
                double ratioStartY = (double) (startPoint.y - cLoc.y) / targetCanvas.getHeight();
                double ratioEndX = (double) (endPoint.x - cLoc.x) / targetCanvas.getWidth();
                double ratioEndY = (double) (endPoint.y - cLoc.y) / targetCanvas.getHeight();

                ratioStartX = Math.max(0, Math.min(1, ratioStartX));
                ratioStartY = Math.max(0, Math.min(1, ratioStartY));
                ratioEndX = Math.max(0, Math.min(1, ratioEndX));
                ratioEndY = Math.max(0, Math.min(1, ratioEndY));

                if (Math.abs(startPoint.x - endPoint.x) < 15 && Math.abs(startPoint.y - endPoint.y) < 15) {
                    dashboard.log("⚡ Sinyal Tap dikirim...");
                } else {
                    dashboard.log("⚡ Sinyal Swipe dikirim...");
                }

                broadcastTouch(ratioStartX, ratioStartY, ratioEndX, ratioEndY, duration);
            }
        };

        syncOverlayDialog.addMouseListener(ma);
        syncOverlayDialog.addMouseMotionListener(ma);
    }

private void broadcastTouch(double startX, double startY, double endX, double endY, long durationMs) {
        // 1. Panggil getConnectedDevices dari adbService
        List<String> devices = dashboard.adbService.getConnectedDevices();
        if (devices.isEmpty()) return;

        int baseW = 1080;
        int baseH = 2400;

        int x1 = (int) (startX * baseW);
        int y1 = (int) (startY * baseH);
        int x2 = (int) (endX * baseW);
        int y2 = (int) (endY * baseH);

        for (String id : devices) {
            dashboard.executor.submit(() -> {
                if (Math.abs(x1 - x2) < 15 && Math.abs(y1 - y2) < 15) {
                    // 2. Panggil runCommand dari adbService (Sintaks dan titik koma sudah tepat)
                    dashboard.adbService.runCommand(dashboard.adbExecutable, "-s", id, "shell", "input", "tap", String.valueOf(x1), String.valueOf(y1));
                } else {
                    long safeDuration = Math.max(100, durationMs);
                    dashboard.adbService.runCommand(dashboard.adbExecutable, "-s", id, "shell", "input", "swipe",
                            String.valueOf(x1), String.valueOf(y1),
                            String.valueOf(x2), String.valueOf(y2),
                            String.valueOf(safeDuration));
                }
            });
        }
    }

    // Method publik untuk dipanggil oleh MainDashboard
    public void updatePosition() {
        if (syncOverlayDialog != null) {
            Point loc = panelLayar.getLocationOnScreen();
            Dimension size = panelLayar.getSize();
            syncOverlayDialog.setBounds(loc.x, loc.y, size.width, size.height);
        }
    }

    // Method publik untuk menyalakan/mematikan kaca
    public void setVisible(boolean visible) {
        if (syncOverlayDialog != null) {
            syncOverlayDialog.setVisible(visible);
        }
    }
}