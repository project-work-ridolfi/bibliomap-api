package it.unipegaso.api.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

public class ImageUtils {

    // resize and convert to base64 jpg
    public static String resizeAndConvertToBase64(byte[] originalImageBytes, int targetWidth) throws IOException {
       
    	ByteArrayInputStream bais = new ByteArrayInputStream(originalImageBytes);
        BufferedImage originalImage = ImageIO.read(bais);

        if (originalImage == null) return null;

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        // calculate height keeping aspect ratio
        int targetHeight = (int) ((double) targetWidth / originalWidth * originalHeight);

        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        graphics.drawImage(originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "jpg", baos);
        
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}