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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import com.wolfesoftware.anodynemapper.resources.Resources;

public class Main
{
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color SEMI_TRANSPARENT_YELLOW = new Color(255, 255, 0, 64);
    private static final int MAP_SIZE = 160;
    private static final double MAX_WALKING_SPEED = MAP_SIZE / 1200.0;
    private static final double MAX_SCROLLING_SPEED = MAP_SIZE / 400.0;
    private static final int TOP_BAR_SIZE = 20;
    private static Robot robot;

    private static Point windowLocation;
    private static Rectangle mapRectangle;
    private static JButton findWindowButton;
    private static JButton startRecordingButton;
    private static BufferedImage currentImage;
    private static Raster currentRaster;
    private static JPanel screenDisplay;

    private static long lastCaptureTime = 0;
    private static double dampenedFps = 0.0;

    private static MappingSession session;

    // capture as fast as possible. in practice this is 30Hz and near 0% CPU usage.
    // maybe this time class is the wrong solution to timing.
    private static Timer captureTimer = new Timer(0, new ActionListener() {
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
    private static JLabel statusLabel;
    private static JPanel trackingGraph;
    private static JPanel mapDisplay;

    public static void main(String[] args) throws AWTException
    {
        robot = new Robot();

        JFrame mainWindow = new JFrame("Anodyne Mapper");
        mainWindow.setSize(MAP_SIZE * 6, MAP_SIZE * 5);
        JPanel mainPanel = new JPanel();
        mainWindow.getContentPane().add(mainPanel);
        mainPanel.setLayout(new GridBagLayout());

        GridBagConstraints layoutData = new GridBagConstraints();
        int rowCount = 0;

        findWindowButton = new JButton("Find Window");
        layoutData.gridx = 0;
        layoutData.gridy = rowCount++;
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
        layoutData.gridy = rowCount++;
        mainPanel.add(startRecordingButton, layoutData);
        startRecordingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                session = new MappingSession();
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
        layoutData.gridy = rowCount++;
        layoutData.fill = GridBagConstraints.BOTH;
        mainPanel.add(screenDisplay, layoutData);

        statusLabel = new JLabel();
        statusLabel.setText(" ");
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setVerticalAlignment(SwingConstants.TOP);
        layoutData.gridx = 0;
        layoutData.gridy = rowCount++;
        layoutData.fill = GridBagConstraints.BOTH;
        mainPanel.add(statusLabel, layoutData);

        trackingGraph = new JPanel() {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                if (session == null)
                    return;
                g.setColor(new Color(0, 0, 0));
                g.fillRect(0, 0, MAP_SIZE, MAP_SIZE);

                int threshold1 = speedToY(MAX_WALKING_SPEED);
                int threshold2 = speedToY(MAP_SIZE / 500.0);
                g.setColor(new Color(32, 32, 32));
                g.fillRect(0, threshold2, MAP_SIZE, threshold1 - threshold2);
                g.fillRect(0, threshold2, MAP_SIZE, threshold1 - threshold2);

                long originT = session.now - 1000;
                ArrayList<Entry<Long, YoungPosition>> positionEntries = new ArrayList<>(session.youngLocationHistory.entrySet());
                lineEndLoop: for (int endIndex = 0; endIndex < positionEntries.size(); endIndex++) {
                    Entry<Long, YoungPosition> entry2 = positionEntries.get(endIndex);
                    if (entry2.getValue() == null)
                        continue;
                    // look backwards for where this line starts
                    for (int startIndex = endIndex - 1; startIndex >= 0; startIndex--) {
                        Entry<Long, YoungPosition> entry1 = positionEntries.get(startIndex);
                        if (entry1.getValue() != null) {
                            // start here
                            int t1 = (int)((entry1.getKey() - originT) * MAP_SIZE / 1000);
                            int t2 = (int)((entry2.getKey() - originT) * MAP_SIZE / 1000);
                            int x1 = entry1.getValue().location.x;
                            int y1 = entry1.getValue().location.y;
                            int x2 = entry2.getValue().location.x;
                            int y2 = entry2.getValue().location.y;
                            if (endIndex - startIndex != 1) {
                                int tmid = (int)((positionEntries.get(endIndex - 1).getKey() - originT) * MAP_SIZE / 1000);
                                int numerator = tmid - t1;
                                int denominator = t2 - t1;
                                int xmid = x1 * (denominator - numerator) / denominator + x2 * numerator / denominator;
                                int ymid = y1 * (denominator - numerator) / denominator + y2 * numerator / denominator;
                                g.setColor(new Color(64, 64, 64));
                                g.drawLine(t1, x1, tmid, xmid);
                                g.drawLine(t1, y1, tmid, ymid);
                                t1 = tmid;
                                x1 = xmid;
                                y1 = ymid;
                            }
                            g.setColor(new Color(255, 0, 0));
                            g.drawLine(t1, x1, t2, x2);
                            g.setColor(new Color(0, 0, 255));
                            g.drawLine(t1, y1, t2, y2);
                            continue lineEndLoop;
                        }
                    }
                    // all previous locations are unknown. don't draw anything
                }

                ArrayList<Entry<Long, Double>> speedEntries = new ArrayList<>(session.youngSpeedHistory.entrySet());
                lineEndLoop: for (int endIndex = 0; endIndex < speedEntries.size(); endIndex++) {
                    Entry<Long, Double> entry2 = speedEntries.get(endIndex);
                    if (entry2.getValue() == null)
                        continue;
                    // look backwards for where this line starts
                    for (int startIndex = endIndex - 1; startIndex >= 0; startIndex--) {
                        Entry<Long, Double> entry1 = speedEntries.get(startIndex);
                        if (entry1.getValue() != null) {
                            // start here
                            int t1 = (int)((entry1.getKey() - originT) * MAP_SIZE / 1000);
                            int t2 = (int)((entry2.getKey() - originT) * MAP_SIZE / 1000);
                            int y1 = speedToY(entry1.getValue());
                            int y2 = speedToY(entry2.getValue());
                            if (endIndex - startIndex != 1) {
                                int tmid = (int)((speedEntries.get(endIndex - 1).getKey() - originT) * MAP_SIZE / 1000);
                                int numerator = tmid - t1;
                                int denominator = t2 - t1;
                                int ymid = y1 * (denominator - numerator) / denominator + y2 * numerator / denominator;
                                g.setColor(new Color(64, 64, 64));
                                g.drawLine(t1, y1, tmid, ymid);
                                t1 = tmid;
                                y1 = ymid;
                            }
                            g.setColor(new Color(0, 255, 0));
                            g.drawLine(t1, y1, t2, y2);
                            continue lineEndLoop;
                        }
                    }
                    // all previous locations are unknown. don't draw anything
                }
            }

            private int speedToY(double v)
            {
                return MAP_SIZE - (int)(v * 300);
            }
        };
        trackingGraph.setPreferredSize(new Dimension(MAP_SIZE, MAP_SIZE));
        layoutData.gridx = 0;
        layoutData.gridy = rowCount++;
        layoutData.fill = GridBagConstraints.BOTH;
        mainPanel.add(trackingGraph, layoutData);

        mapDisplay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                if (session == null)
                    return;
                for (int y = session.minY; y <= session.maxY; y++) {
                    for (int x = session.minX; x <= session.maxX; x++) {
                        g.drawImage(currentImage, (x - session.minX) * MAP_SIZE, (y - session.minY) * MAP_SIZE, null);
                        if (session.currentX == x && session.currentY == y) {
                            g.setColor(SEMI_TRANSPARENT_YELLOW);
                            g.fillRect((x - session.minX) * MAP_SIZE, (y - session.minY) * MAP_SIZE, MAP_SIZE, MAP_SIZE);
                        }
                    }
                }
            }
        };
        layoutData.gridx = 1;
        layoutData.gridy = 0;
        layoutData.gridheight = rowCount;
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
                            continue; // transparent. anything goes.
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

    private enum SpeedTier {
        STANDING, //
        WALKING, //
        SCROLLING, //
        TOO_FAST, //
    }
    private static SpeedTier speedTier;

    private static void captureScreen()
    {
        setCurrentImage(robot.createScreenCapture(mapRectangle));

        // analyze
        long now = System.currentTimeMillis();
        youngPosition = findYoung();
        if (session != null) {
            session.now = now;
            // clean up old times
            cleanupOldTimes(session.youngLocationHistory, now - 1000);
            cleanupOldTimes(session.youngSpeedHistory, now - 1000);
            session.youngLocationHistory.put(now, youngPosition);

            if (youngPosition != null) {
                ArrayList<Entry<Long, YoungPosition>> lastPositions = getLastPositions(session.youngLocationHistory, 5);
                if (lastPositions.size() > 3) {
                    double[] speeds = new double[lastPositions.size() - 1];
                    for (int i = 1; i < lastPositions.size(); i++) {
                        Entry<Long, YoungPosition> entry1 = lastPositions.get(i - 1);
                        Entry<Long, YoungPosition> entry2 = lastPositions.get(i);
                        YoungPosition position1 = entry1.getValue();
                        YoungPosition position2 = entry2.getValue();
                        int dx = position2.location.x - position1.location.x;
                        int dy = position2.location.y - position1.location.y;
                        speeds[i - 1] = Math.sqrt(dx * dx + dy * dy) / (entry2.getKey() - entry1.getKey());
                    }
                    double speed = getProbableSpeed(speeds);
                    session.youngSpeedHistory.put(now, speed);
                    if (speed == 0.0) {
                        speedTier = SpeedTier.STANDING;
                    } else if (speed < MAX_WALKING_SPEED) {
                        speedTier = SpeedTier.WALKING;
                    } else if (speed < MAX_SCROLLING_SPEED) {
                        speedTier = SpeedTier.SCROLLING;
                    } else {
                        speedTier = SpeedTier.TOO_FAST;
                    }
                } else {
                    speedTier = null;
                }
            }
        }

        // repaint
        screenDisplay.repaint();
        mapDisplay.repaint();

        // fps
        if (lastCaptureTime != 0) {
            double instantFps = 1000.0 / (now - lastCaptureTime);
            dampenedFps = dampenedFps * 0.9 + instantFps * 0.1;
        }
        lastCaptureTime = now;

        // update status label
        {
            String displayFps = String.valueOf(Math.floor(dampenedFps * 10) / 10);
            String displayYoungPos = youngPosition != null ? pointToString(youngPosition.location) : "???";
            String displaySpeedTier = speedTier != null ? speedTier.name() : "???";
            statusLabel.setText("<html><pre>" + //
                    "fps: " + displayFps + "\n" + //
                    "young pos: " + displayYoungPos + "\n" + //
                    "speed tier: " + displaySpeedTier + "\n" + //
                    "</pre></html>");
            trackingGraph.repaint();
        }
    }

    private static double getProbableSpeed(double[] speeds)
    {
        // stopped is easy to identify
        if (speeds.length >= 2 && speeds[speeds.length - 1] == 0 && speeds[speeds.length - 2] == 0)
            return 0;

        double sum = 0;
        for (double v : speeds)
            sum += v;
        return sum / speeds.length;
    }

    private static ArrayList<Entry<Long, YoungPosition>> getLastPositions(TreeMap<Long, YoungPosition> history, int n)
    {
        ArrayList<Entry<Long, YoungPosition>> result = new ArrayList<>(n);
        if (history.isEmpty())
            return result;
        Long time = history.lastKey();
        while (true) {
            Entry<Long, YoungPosition> entry = history.lowerEntry(time);
            if (entry == null)
                return result;
            time = entry.getKey();
            if (entry.getValue() != null) {
                result.add(0, entry);
                if (result.size() >= n)
                    return result;
            } else {
                n--;
            }
        }
    }

    private static String pointToString(Point location)
    {
        return "[" + rjust(location.x, 3) + "," + rjust(location.y, 3) + "]";
    }

    private static String rjust(int n, int width)
    {
        String number = String.valueOf(n);
        StringBuilder result = new StringBuilder();
        while (result.length() + number.length() < width)
            result.append(' ');
        result.append(number);
        return result.toString();
    }

    private static <T> void cleanupOldTimes(TreeMap<Long, T> history, long time)
    {
        Iterator<Long> iterator = history.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next() < time)
                iterator.remove();
            else
                break;
        }
    }

    private static class YoungPosition
    {
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

    private static class MappingSession
    {
        public int currentX;
        public int currentY;
        public int minX;
        public int minY;
        public int maxX;
        public int maxY;

        public long now = System.currentTimeMillis();
        public final TreeMap<Long, YoungPosition> youngLocationHistory = new TreeMap<>();
        public final TreeMap<Long, Double> youngSpeedHistory = new TreeMap<>();
    }
}
