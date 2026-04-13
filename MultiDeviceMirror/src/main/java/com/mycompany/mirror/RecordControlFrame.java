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
        btnOpenFolder = new javax.swing.JButton();

        setResizable(false);

        jPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 5));

        lblTimer.setFont(new java.awt.Font("Segoe UI", 0, 18)); // NOI18N
        lblTimer.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTimer.setText("00:00:00");
        lblTimer.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jPanel1.add(lblTimer);

        chkAutoRecord.setText("Auto");
        jPanel1.add(chkAutoRecord);

        tglRecord.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/screen_record.png"))); // NOI18N
        tglRecord.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/stop_circle.png"))); // NOI18N
        tglRecord.addActionListener(this::tglRecordActionPerformed);
        jPanel1.add(tglRecord);

        btnOpenFolder.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder.png"))); // NOI18N
        btnOpenFolder.setText("Open Folder");
        btnOpenFolder.addActionListener(this::btnOpenFolderActionPerformed);
        jPanel1.add(btnOpenFolder);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 461, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
            // ==========================================
            // KONDISI ON: MULAI MEREKAM
            // ==========================================
            if (deviceId != null && windowTitle != null) {
                dashboard.scrcpyService.stop(windowTitle);
                dashboard.scrcpyService.start(deviceId, windowTitle, true);
                
                tglRecord.setText("Stop");
                tglRecord.setBackground(Color.RED);
                dashboard.log("🎥 Recording dimulai...");
                
                // 🔥 Nyalakan Timer
                elapsedSeconds = 0;
                lblTimer.setText("00:00:00");
                lblTimer.setForeground(Color.RED); // Ubah warna jadi merah tanda sedang merekam
                recordTimer.start();
                
            } else {
                tglRecord.setSelected(false);
                JOptionPane.showMessageDialog(this, "Pilih HP aktif dari daftar perangkat!");
            }
        } else {
            // ==========================================
            // KONDISI OFF: BERHENTI MEREKAM & KEMBALI KE SLOT
            // ==========================================
            if (deviceId != null && windowTitle != null) {
                java.awt.Canvas currentCanvas = dashboard.getCanvasFromSlot(windowTitle);
                
                dashboard.scrcpyService.stop(windowTitle);
                
                if (currentCanvas != null) {
                    dashboard.scrcpyService.start(deviceId, windowTitle, false);
                    
                    dashboard.scrcpyService.embed(
                        windowTitle, 
                        currentCanvas, 
                        dashboard, 
                        dashboard.getSpinX(), 
                        dashboard.getSpinY()
                    );
                    
                    dashboard.log("🛑 Record Selesai. Video berdurasi " + lblTimer.getText() + " disimpan.");
                }
                
                // 🔥 Matikan Timer
                recordTimer.stop();
                lblTimer.setForeground(Color.WHITE); // Kembalikan ke warna mati
            }
            tglRecord.setText("Start");
            tglRecord.setBackground(Color.DARK_GRAY);
        }
    }//GEN-LAST:event_tglRecordActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnOpenFolder;
    private javax.swing.JCheckBox chkAutoRecord;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel lblTimer;
    private javax.swing.JToggleButton tglRecord;
    // End of variables declaration//GEN-END:variables
}
