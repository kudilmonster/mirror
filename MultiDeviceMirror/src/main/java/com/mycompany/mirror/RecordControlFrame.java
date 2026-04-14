package com.mycompany.mirror;

import javax.swing.JOptionPane;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;

public class RecordControlFrame extends javax.swing.JFrame {

    private MainDashboard dashboard;

    // 🔥 Variabel untuk Timer Live
    private Timer recordTimer;
    private int elapsedSeconds = 0;

    public RecordControlFrame(MainDashboard dashboard) {
        this.dashboard = dashboard;
        initComponents();

        this.setAlwaysOnTop(true);

        if (dashboard != null) {
            dashboard.setRecordingEnabled(this.chkAutoRecord);
        }

        // 🔥 Inisialisasi Timer (Berjalan setiap 1000ms / 1 detik)
        recordTimer = new Timer(1000, e -> {
            elapsedSeconds++;
            int hours = elapsedSeconds / 3600;
            int minutes = (elapsedSeconds % 3600) / 60;
            int seconds = elapsedSeconds % 60;

            // Format angka menjadi HH:MM:SS (contoh 00:05:09)
            lblTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        });
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        lblTimer = new javax.swing.JLabel();
        chkAutoRecord = new javax.swing.JCheckBox();
        tglRecord = new javax.swing.JToggleButton();
        tglAudio = new javax.swing.JToggleButton();
        btnOpenFolder = new javax.swing.JButton();

        setResizable(false);

        lblTimer.setFont(new java.awt.Font("Yu Gothic UI Semilight", 0, 18)); // NOI18N
        lblTimer.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTimer.setText("00:00:00");
        lblTimer.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jPanel1.add(lblTimer);

        chkAutoRecord.setText("Auto");
        chkAutoRecord.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jPanel1.add(chkAutoRecord);

        tglRecord.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/screen_record.png"))); // NOI18N
        tglRecord.setText("REC");
        tglRecord.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/stop_circle.png"))); // NOI18N
        tglRecord.addActionListener(this::tglRecordActionPerformed);
        jPanel1.add(tglRecord);

        tglAudio.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/mobile_speaker.png"))); // NOI18N
        tglAudio.setText("Spiker");
        tglAudio.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tglAudio.addActionListener(this::tglAudioActionPerformed);
        jPanel1.add(tglAudio);

        btnOpenFolder.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder.png"))); // NOI18N
        btnOpenFolder.setText("Open");
        btnOpenFolder.addActionListener(this::btnOpenFolderActionPerformed);
        jPanel1.add(btnOpenFolder);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 457, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnOpenFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpenFolderActionPerformed
        try {
            java.io.File folder = new java.io.File("recordings");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            java.awt.Desktop.getDesktop().open(folder);
        } catch (Exception ex) {
            if (dashboard != null) {
                dashboard.log("Gagal membuka folder rekaman.");
            }
        }
    }//GEN-LAST:event_btnOpenFolderActionPerformed

    private void tglRecordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tglRecordActionPerformed
        String deviceId = dashboard.getJListDevices().getSelectedValue();
        String windowTitle = dashboard.getSelectedWindowTitle();

        if (tglRecord.isSelected()) {
            if (deviceId != null && windowTitle != null) {
                dashboard.scrcpyService.stop(windowTitle);

                // 🔥 GUNAKAN STATUS TOMBOL AUDIO LOKAL (tglAudio.isSelected()) BUKAN DASHBOARD
                dashboard.scrcpyService.start(deviceId, windowTitle, true, tglAudio.isSelected());

                tglRecord.setText("Stop");
                dashboard.log("🎥 Recording dimulai... (Audio Mute: " + tglAudio.isSelected() + ")");

                elapsedSeconds = 0;
                lblTimer.setText("00:00:00");
                lblTimer.setForeground(Color.RED);
                recordTimer.start();

            } else {
                tglRecord.setSelected(false);
                JOptionPane.showMessageDialog(this, "Pilih HP aktif dari daftar perangkat!");
            }
        } else {
            if (deviceId != null && windowTitle != null) {
                java.awt.Canvas currentCanvas = dashboard.getCanvasFromSlot(windowTitle);

                dashboard.scrcpyService.stop(windowTitle);

                if (currentCanvas != null) {
                    // 🔥 GUNAKAN STATUS TOMBOL AUDIO LOKAL
                    dashboard.scrcpyService.start(deviceId, windowTitle, false, tglAudio.isSelected());

                    dashboard.scrcpyService.embed(
                            windowTitle,
                            currentCanvas,
                            dashboard,
                            dashboard.getSpinX(),
                            dashboard.getSpinY()
                    );

                    dashboard.log("🛑 Record Selesai. Video berdurasi " + lblTimer.getText() + " disimpan.");
                }

                recordTimer.stop();
                lblTimer.setForeground(Color.GRAY);
            }
            tglRecord.setText("Start");
        }
    }//GEN-LAST:event_tglRecordActionPerformed

    private void tglAudioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tglAudioActionPerformed
        boolean muted = tglAudio.isSelected();

        // Ubah teks tombol
        if (muted) {
            tglAudio.setText("OFF");
        } else {
            tglAudio.setText("ON");
        }

        // Simpan status pilihanmu ke Dashboard
        if (dashboard != null) {
            dashboard.isAudioMuted = muted;
        }
    }//GEN-LAST:event_tglAudioActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnOpenFolder;
    private javax.swing.JCheckBox chkAutoRecord;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel lblTimer;
    private javax.swing.JToggleButton tglAudio;
    private javax.swing.JToggleButton tglRecord;
    // End of variables declaration//GEN-END:variables
}
