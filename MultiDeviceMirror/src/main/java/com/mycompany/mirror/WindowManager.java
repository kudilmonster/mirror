package com.mycompany.mirror;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WindowManager {

    private Map<String, HWND> windows = new ConcurrentHashMap<>();

    public void register(String title) {
        new Thread(() -> {
            try {
                HWND hwnd = null;

                for (int i = 0; i < 10; i++) {
                    hwnd = User32.INSTANCE.FindWindow(null, title);
                    if (hwnd != null) break;
                    Thread.sleep(500);
                }

                if (hwnd != null) {
                    windows.put(title, hwnd);
                    log("Window ketemu: " + title);
                } else {
                    log("Window tidak ditemukan: " + title);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void move(String title, int x, int y, int w, int h) {
        HWND hwnd = windows.get(title);

        if (hwnd == null || !User32.INSTANCE.IsWindow(hwnd)) return;

        int flags = 0x0040 | 0x0010 | 0x0004;

        User32.INSTANCE.SetWindowPos(
                hwnd,
                null,
                x,
                y,
                w,
                h,
                flags
        );
    }

    public Map<String, HWND> getAll() {
        return windows;
    }

    private void log(String msg) {
        System.out.println("[WIN] " + msg);
    }
}