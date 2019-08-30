package com.imooc.spring.reactive.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static java.lang.System.out;

/**
 * @Auther: langdylan
 * @Date: 2019-08-29 10:27
 * @Description:
 */
public class JavaGUI {
    /**
     * [Thread: main]
     * [Thread: AWT-EventQueue-0] mouse click coordinate(X: 150, Y: 78)
     * [Thread: AWT-EventQueue-0] mouse click coordinate(X: 224, Y: 128)
     * [Thread: AWT-EventQueue-0] close jFrame...
     * [Thread: AWT-EventQueue-0] exit jFrame...
     *
     * @param args
     */
    public static void main(String[] args) {
        JFrame jFrame = new JFrame("GUI 示例");
        jFrame.setBounds(500, 300, 400, 300);
        LayoutManager layoutManager = new BorderLayout(400, 300);
        jFrame.setLayout(layoutManager);
        jFrame.addMouseListener(new MouseAdapter() { //callback 1
            @Override
            public void mouseClicked(MouseEvent e) {
                out.printf("[Thread: %s] mouse click coordinate(X: %d, Y: %d)\n", currentThreadName(), e.getX(), e.getY());
            }
        });
        jFrame.addWindowListener(new WindowAdapter() { //callback 2
            @Override
            public void windowClosing(WindowEvent e) {
                out.printf("[Thread: %s] close jFrame...\n", currentThreadName());
                jFrame.dispose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                out.printf("[Thread: %s] exit jFrame...\n", currentThreadName());
                System.exit(0);
            }
        });
        out.printf("[Thread: %s] \n", currentThreadName());
        jFrame.setVisible(true);
    }

    private static String currentThreadName() {
        return Thread.currentThread().getName();
    }
}
