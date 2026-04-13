package com.mycompany.mirror;

import javax.swing.JOptionPane;

public class RecordControlFrame extends javax.swing.JFrame {

    private MainDashboard dashboard;

    // Modifikasi constructor agar menerima parameter MainDashboard
    public RecordControlFrame(MainDashboard dashboard) {
        this.dashboard = dashboard;
        initComponents(); // Ini jangan dihapus (punya NetBeans)

        this.setAlwaysOnTop(true);

        if (dashboard != null) {
            dashboard.setRecordingEnabled(this.chkAutoRecord); // chkAutoRecord adalah nama variable checkbox kamu
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        chkAutoRecord = new javax.swing.JCheckBox();
        tglRecord = new javax.swing.JToggleButton();
        btnOpenFolder = new javax.swing.JButton();

        setResizable(false);

        jPanel1.setLayout(new java.awt.GridBagLayout());

        chkAutoRecord.setText("REC");
        jPanel1.add(chkAutoRecord, new java.awt.GridBagConstraints());

        tglRecord.setText("Start Record");
        tglRecord.addActionListener(this::tglRecordActionPerformed);
        jPanel1.add(tglRecord, new java.awt.GridBagConstraints());

        btnOpenFolder.setText("Open Folder");
        btnOpenFolder.addActionListener(this::btnOpenFolderActionPerformed);
        jPanel1.add(btnOpenFolder, new java.awt.GridBagConstraints());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 367, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnOpenFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpenFolderActionPerformed
        try {
            java.io.File folder = new java.io.File("recordings");
            if (!folder.exists()) {
                folder.mkdirs(); // Buat foldernya jika belum ada
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
        // --- LOGIKA ON (Sama seperti sebelumnya) ---
        if (deviceId != null && windowTitle != null) {
            dashboard.scrcpyService.stop(windowTitle);
            dashboard.scrcpyService.start(deviceId, windowTitle, true);
            tglRecord.setText("🛑 Stop Recording");
            dashboard.log("🎥 Recording dimulai...");
        } else {
            tglRecord.setSelected(false);
            JOptionPane.showMessageDialog(this, "Pilih HP aktif!");
        }
    } else {
        // --- LOGIKA OFF: KEMBALI KE SLOT ---
        if (deviceId != null && windowTitle != null) {
            // 1. Ambil Canvas yang sedang ditempati HP ini sebelum dimatikan
            java.awt.Canvas currentCanvas = dashboard.getCanvasFromSlot(windowTitle);
            
            // 2. Matikan scrcpy versi record
            dashboard.scrcpyService.stop(windowTitle);
            
            if (currentCanvas != null) {
                // 3. Jalankan scrcpy biasa (isRecording = false)
                dashboard.scrcpyService.start(deviceId, windowTitle, false);
                
                // 4. LANGSUNG PAKSA MASUK KE KOTAK (Embed)
                dashboard.scrcpyService.embed(
                    windowTitle, 
                    currentCanvas, 
                    dashboard, 
                    dashboard.getSpinX(), 
                    dashboard.getSpinY()
                );
                
                dashboard.log("🛑 Record Selesai. Mirror dikembalikan ke slot.");
            }
        }
        tglRecord.setText("🎥 Start Record");
    }
    }//GEN-LAST:event_tglRecordActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnOpenFolder;
    private javax.swing.JCheckBox chkAutoRecord;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JToggleButton tglRecord;
    // End of variables declaration//GEN-END:variables
}
