package com.wolfesoftware.anodynemapper.resources;

import java.awt.image.Raster;
import java.io.IOException;

import javax.imageio.ImageIO;

public class Resources
{
    public static final Raster menuEnterRaster;
    public static final Raster[] youngSprites = new Raster[12];
    static {
        try {
            menuEnterRaster = ImageIO.read(Resources.class.getResourceAsStream("menu-enter.png")).getData();
            for (int i = 0; i < youngSprites.length; i++)
                youngSprites[i] = ImageIO.read(Resources.class.getResourceAsStream("young_" + i + ".png")).getData();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
