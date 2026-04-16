package com.mycompany.mirror;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ViewerFrame extends JFrame {
    
    // Constructor menerima panelLayar dari MainDashboard
    public ViewerFrame(JPanel panelLayar) {
        setTitle("..::viewer::..");
        setSize(1024, 768);
        setLocationRelativeTo(null); // Posisikan di tengah layar
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); 
        
        //===================ScrollPane=====================
        JScrollPane scrollLayar = new JScrollPane(panelLayar);
        scrollLayar.setBorder(BorderFactory.createEmptyBorder());
        scrollLayar.getVerticalScrollBar().setUnitIncrement(20);
        scrollLayar.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Masukkan ke dalam frame ini
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollLayar, BorderLayout.CENTER);
    }
}