package it.unipegaso.api.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

public class ImageUtils {

    // ridimensiona e converte JPG codificato in Base64
    public static String resizeAndConvertToBase64(byte[] originalImageBytes, int targetWidth) throws IOException {
        // controlla che la larghezza richiesta sia valida
        if (targetWidth <= 0) throw new IllegalArgumentException("targetWidth deve essere > 0");

        // try-with-resources per garantire la chiusura automatica degli stream
        try (ByteArrayInputStream bais = new ByteArrayInputStream(originalImageBytes);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            BufferedImage originalImage = ImageIO.read(bais);
            if (originalImage == null) return null; // restituisce null se l'immagine non è valida

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            // calcola l'altezza target mantenendo le proporzioni
            int targetHeight = (int) ((double) targetWidth / originalWidth * originalHeight);

            // crea un nuovo BufferedImage RGB per la versione ridimensionata
            BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

            // crea un oggetto Graphics2D per disegnare l'immagine ridimensionata
            Graphics2D graphics = resizedImage.createGraphics();

            // Imposta suggerimenti per migliorare la qualità del ridimensionamento
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // disegna l'immagine originale ridimensionata sul nuovo BufferedImage
            graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose(); // rilascia le risorse del Graphics2D

            ImageIO.write(resizedImage, "jpg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }
    
}
