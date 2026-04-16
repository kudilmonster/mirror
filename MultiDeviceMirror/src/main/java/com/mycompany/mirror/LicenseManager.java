package com.mycompany.mirror;

import java.util.prefs.Preferences;
import javax.swing.JOptionPane;

public class LicenseManager {
    
    // Nama penyimpanan rahasia di dalam sistem operasi (Windows Registry / Mac Plist)
    private static final String PREFS_NODE = "com.mycompany.mirror.license";
    private static final String KEY_STATUS = "is_activated";
    
    // 🔥 UBAH SERIAL NUMBER ANDA DI SINI
    private static final String VALID_SERIAL = "sanFK";

    /**
     * Mengecek apakah aplikasi sudah diaktivasi.
     * @return true jika lisensi valid, false jika tidak valid / dibatalkan.
     */
    public static boolean checkAndVerify() {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        boolean isActivated = prefs.getBoolean(KEY_STATUS, false);

        // Jika sudah pernah aktivasi, langsung izinkan masuk (bypass)
        if (isActivated) {
            return true; 
        }

        // Jika belum, munculkan kotak dialog untuk memasukkan lisensi
        String input = JOptionPane.showInputDialog(
                null,
                "Aplikasi Terkunci.\nMasukkan Serial Number untuk menggunakan Kunyuk Pro:",
                "Aktivasi Lisensi",
                JOptionPane.WARNING_MESSAGE
        );

        // Jika user menekan tombol Cancel / X
        if (input == null) {
            return false;
        }

        // Cek apakah ketikan user sama dengan serial number kita
        if (input.trim().equals(VALID_SERIAL)) {
            // Lisensi Benar! Simpan status ke memori komputer
            prefs.putBoolean(KEY_STATUS, true);
            JOptionPane.showMessageDialog(null, "Aktivasi Berhasil! Terima kasih telah menggunakan Kunyuk Pro.", "Sukses", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } else {
            // Lisensi Salah!
            JOptionPane.showMessageDialog(null, "Serial Number tidak valid!", "Akses Ditolak", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    /**
     * (Opsional) Panggil fungsi ini jika Anda ingin mencabut/mereset lisensi dari PC ini.
     */
    public static void revokeLicense() {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        prefs.putBoolean(KEY_STATUS, false);
    }
}