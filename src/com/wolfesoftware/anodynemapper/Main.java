package com.wolfesoftware.anodynemapper;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
    private static final Color SEMI_TRANSPARENT_YELLOW = new Color(255, 255, 0, 64);
    private static final int MAP_SIZE = 160;
    private static final int TOP_BAR_SIZE = 20;
    private static Robot robot;

    private static Point windowLocation;
    private static Rectangle mapRectangle;
    private static JButton findWindowButton;
    private static JButton startRecordingButton;
    private static BufferedImage currentImage;
    private static Raster currentRaster;
    private static JPanel screenDisplay;

    private static Timer captureTimer = new Timer(1000 / 60, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            captureScreen();
        }
    });
    private static Timer findWindowTimer = new Timer(500, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            tryToFindWindow();
        }
    });
    private static YoungPosition youngPosition;

    public static void main(String[] args) throws AWTException
    {
        robot = new Robot();

        JFrame mainWindow = new JFrame("Anodyne Mapper");
        mainWindow.setSize(500, 300);
        JPanel mainPanel = new JPanel();
        mainWindow.getContentPane().add(mainPanel);
        mainPanel.setLayout(new GridBagLayout());

        GridBagConstraints layoutData = new GridBagConstraints();
        findWindowButton = new JButton("Find Window");
        layoutData.gridx = 0;
        layoutData.gridy = 0;
        mainPanel.add(findWindowButton, layoutData);
        findWindowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (captureTimer.isRunning()) {
                    captureTimer.stop();
                    findWindowButton.setText("Find Window");
                    setCurrentImage(null);
                    screenDisplay.repaint();
                } else if (findWindowTimer.isRunning()) {
                    findWindowTimer.stop();
                    findWindowButton.setText("Find Window");
                } else {
                    findWindowTimer.start();
                    findWindowButton.setText("Cancel");
                }
            }
        });

        startRecordingButton = new JButton("Start Recording");
        startRecordingButton.setEnabled(false);
        layoutData.gridx = 0;
        layoutData.gridy = 1;
        mainPanel.add(startRecordingButton, layoutData);
        startRecordingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
            }
        });

        screenDisplay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                if (currentImage != null) {
                    g.drawImage(currentImage, 0, 0, null);
                    if (youngPosition != null) {
                        Raster sprite = Resources.youngSprites[youngPosition.spriteIndex];
                        g.setColor(SEMI_TRANSPARENT_YELLOW);
                        g.fillRect(youngPosition.location.x, youngPosition.location.y, sprite.getWidth(), sprite.getHeight());
                    }
                }
            }
        };
        screenDisplay.setPreferredSize(new Dimension(MAP_SIZE, MAP_SIZE));
        layoutData.gridx = 0;
        layoutData.gridy = 2;
        layoutData.fill = GridBagConstraints.BOTH;
        mainPanel.add(screenDisplay, layoutData);

        JPanel mapDisplay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
            }
        };
        layoutData.gridx = 1;
        layoutData.gridy = 0;
        layoutData.gridheight = 3;
        layoutData.fill = GridBagConstraints.BOTH;
        layoutData.weightx = 1.0;
        layoutData.weighty = 1.0;
        mainPanel.add(mapDisplay, layoutData);

        mainWindow.setVisible(true);
        mainWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e)
            {
                // TODO: why doesn't the app shut down normally?
                System.exit(0);
            }
        });
    }

    private static void setCurrentImage(BufferedImage image)
    {
        currentImage = image;
        currentRaster = image != null ? image.getData() : null;
    }

    private static void tryToFindWindow()
    {
        Point location = findWindow();
        if (location == null)
            return;
        // got one
        setWindowLocation(location);
        findWindowTimer.stop();
        captureTimer.start();
        findWindowButton.setText("Stop");
    }
    private static void setWindowLocation(Point location)
    {
        windowLocation = location;
        mapRectangle = new Rectangle(windowLocation.x, windowLocation.y + TOP_BAR_SIZE, MAP_SIZE, MAP_SIZE);

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

        startRecordingButton.setEnabled(true);
    }

    private static void setUndecorated(JFrame window)
    {
        window.setUndecorated(true);
        window.setBackground(TRANSPARENT);
        window.setFocusableWindowState(false);
    }

    private static Point findWindow()
    {
        BufferedImage image = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        Raster screenRaster = image.getData();
        return findSubImage(screenRaster, Resources.menuEnterRaster, 0);
    }

    private static Point findSubImage(Raster screenRaster, Raster imageRaster, int errorMargin)
    {
        int[] screenPixel = new int[3];
        int[] imagePixel = new int[4];
        int screenHeight = screenRaster.getHeight();
        int screenWidth = screenRaster.getWidth();
        int imageHeight = imageRaster.getHeight();
        int imageWidth = imageRaster.getWidth();
        for (int windowY = -errorMargin; windowY < screenHeight - imageHeight + errorMargin; windowY++) {
            imageLocation: for (int windowX = -errorMargin; windowX < screenWidth - imageWidth + errorMargin; windowX++) {
                for (int y = errorMargin; y < imageHeight - errorMargin; y++) {
                    for (int x = errorMargin; x < imageWidth - errorMargin; x++) {
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

    private static void captureScreen()
    {
        setCurrentImage(robot.createScreenCapture(mapRectangle));

        youngPosition = findYoung();
        screenDisplay.repaint();
    }

    private static class YoungPosition {
        public final int spriteIndex;
        public final Point location;
        public YoungPosition(int spriteIndex, Point location) {
            this.spriteIndex = spriteIndex;
            this.location = location;
        }
    }
    private static YoungPosition findYoung()
    {
        for (int i = 0; i < Resources.youngSprites.length; i++) {
            Point location = findSubImage(currentRaster, Resources.youngSprites[i], 4);
            if (location != null)
                return new YoungPosition(i, location);
        }
        return null;
    }
}
