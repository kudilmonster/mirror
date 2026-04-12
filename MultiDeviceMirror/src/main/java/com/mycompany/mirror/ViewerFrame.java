package com.mycompany.mirror;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ViewerFrame extends JFrame {
    
    // Constructor menerima panelLayar dari MainDashboard
    public ViewerFrame(JPanel panelLayar) {
        setTitle("Kunyuk.pro - Screen Viewer");
        setSize(1000, 700);
        setLocationRelativeTo(null); // Posisikan di tengah layar
        
        // Cegah user mematikan aplikasi dengan close window ini langsung,
        // biarkan MainDashboard yang mengontrol shutdown.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); 
        
        // Bungkus panelLayar dengan ScrollPane persis seperti kode lamamu
        JScrollPane scrollLayar = new JScrollPane(panelLayar);
        scrollLayar.setBorder(BorderFactory.createEmptyBorder());
        scrollLayar.getVerticalScrollBar().setUnitIncrement(20);
        scrollLayar.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Masukkan ke dalam frame ini
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollLayar, BorderLayout.CENTER);
    }
}