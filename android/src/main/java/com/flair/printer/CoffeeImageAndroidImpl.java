package com.flair.printer;

import android.graphics.Bitmap;

import com.github.anastaciocintra.escpos.image.CoffeeImage;

/**
 * implements CoffeeImage using Java BufferedImage
 * @see CoffeeImage
 * @see Bitmap
 */
public class CoffeeImageAndroidImpl implements CoffeeImage {
    private Bitmap bitmap;

    public CoffeeImageAndroidImpl(Bitmap bitmap) {
        this.bitmap = bitmap;
    }


    @Override
    public int getWidth() {
        return bitmap.getWidth();
    }

    @Override
    public int getHeight() {
        return bitmap.getHeight();
    }

    @Override
    public CoffeeImage getSubimage(int x, int y, int w, int h) {
        return new CoffeeImageAndroidImpl(bitmap.createBitmap(this.bitmap,x,y,w,h));
    }

    @Override
    public int getRGB(int x, int y) {
        return bitmap.getPixel(x, y);
    }
}