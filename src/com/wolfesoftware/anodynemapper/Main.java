package com.wolfesoftware.anodynemapper;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.wolfesoftware.anodynemapper.resources.Resources;

public class Main
{
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final int WINDOW_WIDTH = 160;
    private static final int WINDOW_HEIGHT = 180;
    private static final int MAP_SIZE = 160;
    private static final int TOP_BAR_SIZE = 20;
    private static Robot robot;

    private static Point windowLocation;

    public static void main(String[] args) throws AWTException
    {
        robot = new Robot();

        JFrame mainWindow = new JFrame("Anodyne Mapper");
        mainWindow.setSize(400, 200);
        JPanel mainPanel = new JPanel();
        mainWindow.getContentPane().add(mainPanel);

        JButton findWindowButton = new JButton("Find Window");
        findWindowButton.setSize(100, 50);
        mainPanel.add(findWindowButton);
        findWindowButton.addActionListener(new ActionListener() {
            private Timer timer;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (timer == null) {
                    ActionListener listener = new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e)
                        {
                            Point location = findWindow();
                            if (location != null) {
                                // got one
                                setWindowLocation(location);
                                stopTimer();
                            }
                        }
                    };
                    timer = new Timer(500, listener);
                    findWindowButton.setText("Cancel");
                    timer.start();
                    // and do it now
                    listener.actionPerformed(null);
                } else {
                    // cancel
                    stopTimer();
                }
            }
            private void stopTimer()
            {
                timer.stop();
                timer = null;
                findWindowButton.setText("Find Window");
            }
        });

        mainWindow.setVisible(true);
    }

    private static Point findWindow()
    {
        BufferedImage image = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        Raster screenRaster = image.getData();
        return findSubImage(screenRaster, Resources.menuEnterRaster);
    }
    private static void setWindowLocation(Point location)
    {
        windowLocation = location;

        JFrame highlightFrame = new JFrame();
        highlightFrame.setSize(MAP_SIZE + 16, MAP_SIZE + 16);
        highlightFrame.setLocation(windowLocation.x - 8, windowLocation.y + TOP_BAR_SIZE - 8);
        JPanel highlightRectangle = new JPanel() {
            {
                setBackground(TRANSPARENT);
            }
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);

                g.setColor(new Color(255, 255, 0));
//                g.fillRect(0, 0, WINDOW_WIDTH - 10, WINDOW_HEIGHT - 10);
                g.fillRect(0, 0, 8, MAP_SIZE + 16);
                g.fillRect(MAP_SIZE + 8, 0, 8, MAP_SIZE + 16);
                g.fillRect(0, 0, MAP_SIZE + 16, 8);
                g.fillRect(0, MAP_SIZE + 8, MAP_SIZE + 16, 8);
            }
        };
        highlightFrame.getContentPane().add(highlightRectangle);
        setUndecorated(highlightFrame);
        highlightFrame.setVisible(true);

        // turn it off after a few seconds
        Timer timer = new Timer(2000, null);
        timer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                highlightFrame.dispose();
                timer.stop();
            }
        });
        timer.start();
    }

    private static void setUndecorated(JFrame window)
    {
        window.setUndecorated(true);
        window.setBackground(TRANSPARENT);
        window.setFocusableWindowState(false);
    }

    private static Point findSubImage(Raster screenRaster, Raster imageRaster)
    {
        int[] screenPixel = new int[3];
        int[] imagePixel = new int[4];
        int screenHeight = screenRaster.getHeight();
        int screenWidth = screenRaster.getWidth();
        int imageHeight = imageRaster.getHeight();
        int imageWidth = imageRaster.getWidth();
        for (int windowY = 0; windowY < screenHeight - imageHeight; windowY++) {
            imageLocation: for (int windowX = 0; windowX < screenWidth - imageWidth; windowX++) {
                for (int y = 0; y < imageHeight; y++) {
                    for (int x = 0; x < imageWidth; x++) {
                        screenRaster.getPixel(windowX + x, windowY + y, screenPixel);
                        imageRaster.getPixel(x, y, imagePixel);
                        if (imagePixel[3] == 0)
                            continue; // transperant. anything goes.
                        if (imagePixel[3] != 255)
                            throw null; // partially transparent.
                        if (!(screenPixel[0] == imagePixel[0] && screenPixel[1] == imagePixel[1] && screenPixel[2] == imagePixel[2]))
                            continue imageLocation; // mismatch
                        // match
                    }
                }
                // all pixels match
                return new Point(windowX, windowY);
            }
        }
        return null;
    }
}
