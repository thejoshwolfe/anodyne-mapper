package com.wolfesoftware.anodynemapper.resources;

import java.awt.image.Raster;
import java.io.IOException;

import javax.imageio.ImageIO;

public class Resources
{
    public static final Raster menuEnterRaster;
    static {
        try {
            menuEnterRaster = ImageIO.read(Resources.class.getResourceAsStream("menu-enter.png")).getData();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
