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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.wolfesoftware.anodynemapper.resources.Resources;

public class Main
{
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color SEMI_TRANSPARENT_YELLOW = new Color(255, 255, 0, 64);
    private static final Color SEMI_TRANSPARENT_GREEN = new Color(0, 255, 0, 64);
    private static final Color SEMI_TRANSPARENT_BLUE = new Color(0, 0, 255, 64);
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

    private static int selectedMapTileX;
    private static int selectedMapTileY;
    private static int zoomFactor = 1;

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

        JButton saveButton = new JButton("Export...");
        layoutData.gridx = 0;
        layoutData.gridy = rowCount++;
        mainPanel.add(saveButton, layoutData);
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (session == null || session.imageMap.isEmpty())
                    return; // TODO disable instead
                boolean wasCapturing = captureTimer.isRunning();
                if (wasCapturing) {
                    captureTimer.stop();
                }

                JFileChooser fileChooser = new JFileChooser(new File("."));
                fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
                fileChooser.setAcceptAllFileFilterUsed(false);
                int result = fileChooser.showSaveDialog(mainWindow);
                if (result != JFileChooser.APPROVE_OPTION)
                    return;
                File file = fileChooser.getSelectedFile();

                BufferedImage outputImage = renderMap();
                try {
                    if (!ImageIO.write(outputImage, "png", file))
                        throw null;
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                if (wasCapturing) {
                    captureTimer.start();
                }
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
                g.setColor(speedTier == SpeedTier.SCROLLING ? new Color(0, 32, 0) : new Color(32, 32, 32));
                g.fillRect(0, threshold2, MAP_SIZE, threshold1 - threshold2);
                g.fillRect(0, threshold2, MAP_SIZE, threshold1 - threshold2);

                long originT = session.now - 1000;
                ArrayList<Entry<Long, YoungPosition>> positionEntries = new ArrayList<>(session.youngLocationHistory.entrySet());
                lineEndLoop: for (int endIndex = 1; endIndex < positionEntries.size(); endIndex++) {
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

                ArrayList<Entry<Long, Velocity>> speedEntries = new ArrayList<>(session.youngSpeedHistory.entrySet());
                for (int endIndex = 1; endIndex < speedEntries.size(); endIndex++) {
                    Entry<Long, Velocity> entry2 = speedEntries.get(endIndex);
                    int startIndex = endIndex - 1;
                    Entry<Long, Velocity> entry1 = speedEntries.get(startIndex);
                    if (entry1.getValue() != null) {
                        // start here
                        int t1 = (int)((entry1.getKey() - originT) * MAP_SIZE / 1000);
                        int t2 = (int)((entry2.getKey() - originT) * MAP_SIZE / 1000);
                        int y1 = speedToY(entry1.getValue().getOrthoSpeed());
                        int y2 = speedToY(entry2.getValue().getOrthoSpeed());
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
                    }
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
                        RecordedScreen record = session.imageMap.get(new Point(x, y));
                        if (record != null) {
                            g.drawImage(record.image, (x - session.minX) * MAP_SIZE / zoomFactor, (y - session.minY) * MAP_SIZE / zoomFactor, MAP_SIZE / zoomFactor, MAP_SIZE / zoomFactor, null);
                        }
                        if (session.current.x == x && session.current.y == y) {
                            if (record != null) {
                                if (!record.isDone()) {
                                    g.setColor(SEMI_TRANSPARENT_YELLOW);
                                } else {
                                    g.setColor(SEMI_TRANSPARENT_GREEN);
                                }
                            } else {
                                g.setColor(SEMI_TRANSPARENT_BLUE);
                            }
                            g.fillRect((x - session.minX) * MAP_SIZE / zoomFactor, (y - session.minY) * MAP_SIZE / zoomFactor, MAP_SIZE / zoomFactor, MAP_SIZE / zoomFactor);
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

        JPopupMenu mapContextMenu = new JPopupMenu();
        mapContextMenu.add(new AbstractAction("I am here") {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                session.current = new Point(selectedMapTileX, selectedMapTileY);
            }
        });
        mapContextMenu.add(new AbstractAction("Erase Tile") {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                session.imageMap.remove(new Point(selectedMapTileX, selectedMapTileY));
                findMinMax();
            }
        });
        mapContextMenu.add(new AbstractAction("Zoom Out") {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (zoomFactor == 1)
                    zoomFactor = 2;
                else if (zoomFactor == 2)
                    zoomFactor = 4;
            }
        });
        mapContextMenu.add(new AbstractAction("Zoom In") {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (zoomFactor == 4)
                    zoomFactor = 2;
                else if (zoomFactor == 2)
                    zoomFactor = 1;
            }
        });
        mapDisplay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (session == null)
                    return;
                selectedMapTileX = e.getX() / (MAP_SIZE / zoomFactor) + session.minX;
                selectedMapTileY = e.getY() / (MAP_SIZE / zoomFactor) + session.minY;
                mapContextMenu.show(mapDisplay, e.getX(), e.getY());
            }
        });

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

    private static BufferedImage renderMap()
    {
        BufferedImage output = new BufferedImage((session.maxX - session.minX + 1) * MAP_SIZE, (session.maxY - session.minY + 1) * MAP_SIZE, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics g = output.getGraphics();
        for (int y = session.minY; y <= session.maxY; y++) {
            for (int x = session.minX; x <= session.maxX; x++) {
                RecordedScreen record = session.imageMap.get(new Point(x, y));
                if (record != null) {
                    g.drawImage(record.image, (x - session.minX) * MAP_SIZE, (y - session.minY) * MAP_SIZE, null);
                }
            }
        }
        output.flush();
        return output;
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
    private static Point scrollDirection;
    private static int stoppedScrollingFramgesAgoCounter;

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
                ArrayList<Entry<Long, YoungPosition>> lastPositions = getLastNonNullEntries(session.youngLocationHistory, 5);
                if (lastPositions.size() > 3) {
                    Velocity[] velocities = new Velocity[lastPositions.size() - 1];
                    for (int i = 1; i < lastPositions.size(); i++) {
                        Entry<Long, YoungPosition> entry1 = lastPositions.get(i - 1);
                        Entry<Long, YoungPosition> entry2 = lastPositions.get(i);
                        YoungPosition position1 = entry1.getValue();
                        YoungPosition position2 = entry2.getValue();
                        int dx = position2.location.x - position1.location.x;
                        int dy = position2.location.y - position1.location.y;
                        double dt = entry2.getKey() - entry1.getKey();
                        velocities[i - 1] = new Velocity(dx / dt, dy / dt);
                    }
                    Velocity speed = getProbableVelocity(velocities);
                    session.youngSpeedHistory.put(now, speed);
                    speedTier = getSpeedTier(speed);
                } else {
                    speedTier = null;
                }
            }

            if (scrollDirection != null) {
                // check for stop scrolling
                if (speedTier != SpeedTier.SCROLLING || !session.youngSpeedHistory.lastEntry().getValue().orthoNormalize().equals(scrollDirection)) {
                    session.current = new Point(session.current.x - scrollDirection.x, session.current.y - scrollDirection.y);
                    findMinMax();
                    scrollDirection = null;
                    stoppedScrollingFramgesAgoCounter = 0;
                }
            } else {
                // check for scrolling start
                ArrayList<Entry<Long, Velocity>> recentSpeeds = getLastNonNullEntries(session.youngSpeedHistory, 3);
                if (recentSpeeds.size() == 3) {
                    boolean scrolling = true;
                    for (Entry<Long, Velocity> entry : recentSpeeds) {
                        if (getSpeedTier(entry.getValue()) != SpeedTier.SCROLLING) {
                            scrolling = false;
                            break;
                        }
                    }
                    if (scrolling)
                        scrollDirection = recentSpeeds.get(recentSpeeds.size() - 1).getValue().orthoNormalize();
                }
            }

            if (youngPosition != null) {
                RecordedScreen record = session.imageMap.get(session.current);
                if (record != null && !record.isDone()) {
                    // check for filling in the gaps
                    Iterator<Point> iterator = record.stillNeedPoints.iterator();
                    Raster sprite = Resources.youngSprites[youngPosition.spriteIndex];
                    int[] pixel = new int[4];
                    while (iterator.hasNext()) {
                        Point point = iterator.next();
                        int spriteX = point.x - youngPosition.location.x;
                        int spriteY = point.y - youngPosition.location.y;
                        if (0 <= spriteX && spriteX < sprite.getWidth() && 0 <= spriteY && spriteY < sprite.getHeight()) {
                            sprite.getPixel(spriteX, spriteY, pixel);
                            if (pixel[3] != 0)
                                continue;
                        }
                        // this one is clear now
                        int rgb = currentImage.getRGB(point.x, point.y);
                        record.image.setRGB(point.x, point.y, rgb);
                        iterator.remove();
                    }
                    record.image.flush();
                } else {
                    if (record == null && scrollDirection == null) {
                        // check for "take a snapshot" command, which is standing still after scrolling
                        if (stoppedScrollingFramgesAgoCounter <= 2 && speedTier == SpeedTier.STANDING) {
                            session.imageMap.put(session.current, new RecordedScreen(currentImage, youngPosition));

                            // don't double take
                            stoppedScrollingFramgesAgoCounter = 10;
                        }
                        stoppedScrollingFramgesAgoCounter++;
                    }
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
            String displayScrollDir = scrollDirection != null ? pointToString(scrollDirection) : "---";
            statusLabel.setText("<html><pre>" + //
                    "fps: " + displayFps + "\n" + //
                    "young pos: " + displayYoungPos + "\n" + //
                    "speed tier: " + displaySpeedTier + "\n" + //
                    "scroll: " + displayScrollDir + "\n" + //
                    "</pre></html>");
            trackingGraph.repaint();
        }
    }

    private static void findMinMax()
    {
        session.minX = session.current.x;
        session.maxX = session.current.x;
        session.minY = session.current.y;
        session.maxY = session.current.y;
        for (Point location : session.imageMap.keySet()) {
            if (location.x < session.minX)
                session.minX = location.x;
            if (location.y < session.minY)
                session.minY = location.y;
            if (location.x > session.maxX)
                session.maxX = location.x;
            if (location.y > session.maxY)
                session.maxY = location.y;
        }
    }

    private static class Velocity
    {
        public final double x;
        public final double y;
        public Velocity(double x, double y) {
            this.x = x;
            this.y = y;
        }
        public Point orthoNormalize()
        {
            if (Math.abs(x) > Math.abs(y))
                return new Point((int)Math.signum(x), 0);
            else
                return new Point(0, (int)Math.signum(y));
        }
        public boolean isZero()
        {
            return x == 0 && y == 0;
        }
        public double getOrthoSpeed()
        {
            return Math.max(Math.abs(x), Math.abs(y));
        }
    }
    private static SpeedTier getSpeedTier(Velocity velocity)
    {
        double speed = velocity.getOrthoSpeed();
        if (speed == 0.0) {
            return SpeedTier.STANDING;
        } else if (speed < MAX_WALKING_SPEED) {
            return SpeedTier.WALKING;
        } else if (speed < MAX_SCROLLING_SPEED) {
            return SpeedTier.SCROLLING;
        } else {
            return SpeedTier.TOO_FAST;
        }
    }

    private static Velocity getProbableVelocity(Velocity[] velocities)
    {
        // stopped is easy to identify
        if (velocities.length >= 2 && velocities[velocities.length - 1].isZero() && velocities[velocities.length - 2].isZero())
            return new Velocity(0, 0);

        double sumX = 0;
        double sumY = 0;
        for (Velocity v : velocities) {
            sumX += v.x;
            sumY += v.y;
        }
        return new Velocity(sumX / velocities.length, sumY / velocities.length);
    }

    private static <T> ArrayList<Entry<Long, T>> getLastNonNullEntries(TreeMap<Long, T> history, int n)
    {
        ArrayList<Entry<Long, T>> result = new ArrayList<>(n);
        if (history.isEmpty())
            return result;
        Long time = history.lastKey();
        while (true) {
            Entry<Long, T> entry = history.lowerEntry(time);
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
        public Point current = new Point(0, 0);
        public int minX;
        public int minY;
        public int maxX;
        public int maxY;

        public long now = System.currentTimeMillis();
        public final TreeMap<Long, YoungPosition> youngLocationHistory = new TreeMap<>();
        public final TreeMap<Long, Velocity> youngSpeedHistory = new TreeMap<>();
        public final HashMap<Point, RecordedScreen> imageMap = new HashMap<>();
    }

    private static class RecordedScreen
    {
        public final BufferedImage image;
        public final HashSet<Point> stillNeedPoints = new HashSet<>();

        public RecordedScreen(BufferedImage image, YoungPosition youngPosition) {
            this.image = image;

            // don't consider young's sprite correct
            Raster sprite = Resources.youngSprites[youngPosition.spriteIndex];
            int[] pixel = new int[4];
            int height = sprite.getHeight();
            int width = sprite.getWidth();
            for (int spriteY = 0; spriteY < height; spriteY++) {
                for (int spriteX = 0; spriteX < width; spriteX++) {
                    sprite.getPixel(spriteX, spriteY, pixel);
                    if (pixel[3] == 0)
                        continue; // transparent pixels are cool
                    int imageX = youngPosition.location.x + spriteX;
                    int imageY = youngPosition.location.y + spriteY;
                    if (0 <= imageX && imageX < MAP_SIZE && 0 <= imageY && imageY < MAP_SIZE) {
                        stillNeedPoints.add(new Point(imageX, imageY));
                    }
                }
            }
        }

        public boolean isDone()
        {
            return stillNeedPoints.isEmpty();
        }
    }
}
