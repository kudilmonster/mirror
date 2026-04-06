package com.mycompany.mirror;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScrcpyService {

    private final String scrcpyPath;

    private Map<String, Process> processes = new ConcurrentHashMap<>();

    public ScrcpyService(String scrcpyPath) {
        this.scrcpyPath = scrcpyPath;
    }

    public void start(String deviceID, int x, int y, int w, int h) {
        if (deviceID == null || deviceID.isEmpty()) return;

        if (processes.containsKey(deviceID)) {
            log("Sudah jalan: " + deviceID);
            return;
        }

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        scrcpyPath,
                        "-s", deviceID,
                        "--window-x", String.valueOf(x),
                        "--window-y", String.valueOf(y),
                        "--window-width", String.valueOf(w),
                        "--window-height", String.valueOf(h),
                        "--window-borderless",
                        "--always-on-top",
                        "--window-title", deviceID,
                        "--no-audio"
                );

                Process p = pb.start();
                processes.put(deviceID, p);

                log("Start scrcpy: " + deviceID);

            } catch (IOException e) {
                log("Error start: " + e.getMessage());
            }
        }).start();
    }

    public void stopAll() {
        for (Process p : processes.values()) {
            try {
                p.destroy();
            } catch (Exception ignored) {}
        }
        processes.clear();
    }

    private void log(String msg) {
        System.out.println("[SCRCPY] " + msg);
    }
}