package com.example.medinex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.InputStream;

public class OcrImagePreprocessor {

    public static class ImageQualityResult {
        public boolean isAcceptable = true;
        public boolean isBlurry = false;
        public boolean isTooDark = false;
        public boolean isOverexposed = false;
        public boolean isLowResolution = false;
        public float meanBrightness = 128f;
        public float sharpnessScore = 100f;
        public String qualityNotice = null;

        @Override
        public String toString() {
            return "ImageQualityResult{" +
                    "isAcceptable=" + isAcceptable +
                    ", isBlurry=" + isBlurry +
                    ", isTooDark=" + isTooDark +
                    ", isOverexposed=" + isOverexposed +
                    ", isLowResolution=" + isLowResolution +
                    ", notice='" + qualityNotice + '\'' +
                    '}';
        }
    }

    /**
     * Performs non-destructive quality assessment on the uploaded image.
     */
    public static ImageQualityResult assessImageQuality(Bitmap bitmap) {
        ImageQualityResult result = new ImageQualityResult();
        if (bitmap == null) {
            result.isAcceptable = false;
            result.qualityNotice = "Image could not be loaded.";
            return result;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width < 300 || height < 300) {
            result.isLowResolution = true;
            result.isAcceptable = false;
            result.qualityNotice = "Image resolution is low (" + width + "x" + height + "). Please crop closer to the medicine label or capture a higher resolution image.";
            return result;
        }

        // Sample a grid of pixels to assess brightness and sharpness efficiently
        int sampleStep = Math.max(1, Math.min(width, height) / 100);
        long totalLuminance = 0;
        int sampleCount = 0;

        // Laplacian edge energy for blur estimation
        double edgeEnergySum = 0;
        int edgeCount = 0;

        int[] rowBufferCurrent = new int[width];
        int[] rowBufferPrev = new int[width];
        int[] rowBufferNext = new int[width];

        bitmap.getPixels(rowBufferPrev, 0, width, 0, 1, width, 1);
        bitmap.getPixels(rowBufferCurrent, 0, width, 0, 2, width, 1);

        for (int y = 2; y < height - 2; y += sampleStep) {
            bitmap.getPixels(rowBufferNext, 0, width, 0, y + 1, width, 1);

            for (int x = 2; x < width - 2; x += sampleStep) {
                int pixel = rowBufferCurrent[x];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                int lum = (r * 299 + g * 587 + b * 114) / 1000;

                totalLuminance += lum;
                sampleCount++;

                // Laplacian response: 4*center - (left + right + top + bottom)
                int leftLum = ((rowBufferCurrent[x - 1] >> 8) & 0xFF);
                int rightLum = ((rowBufferCurrent[x + 1] >> 8) & 0xFF);
                int topLum = ((rowBufferPrev[x] >> 8) & 0xFF);
                int bottomLum = ((rowBufferNext[x] >> 8) & 0xFF);

                int laplacian = (lum << 2) - (leftLum + rightLum + topLum + bottomLum);
                edgeEnergySum += (laplacian * laplacian);
                edgeCount++;
            }

            System.arraycopy(rowBufferCurrent, 0, rowBufferPrev, 0, width);
            System.arraycopy(rowBufferNext, 0, rowBufferCurrent, 0, width);
        }

        if (sampleCount > 0) {
            result.meanBrightness = (float) totalLuminance / sampleCount;
        }

        if (edgeCount > 0) {
            result.sharpnessScore = (float) Math.sqrt(edgeEnergySum / edgeCount);
        }

        // Assess lighting
        if (result.meanBrightness < 35f) {
            result.isTooDark = true;
            result.isAcceptable = false;
            result.qualityNotice = "Image is too dark. Please take the photo in a well-lit area.";
        } else if (result.meanBrightness > 238f) {
            result.isOverexposed = true;
            result.isAcceptable = false;
            result.qualityNotice = "Image has severe glare or overexposure. Avoid direct flash on blister foil.";
        }

        // Assess blurriness
        if (result.sharpnessScore < 6.5f) {
            result.isBlurry = true;
            result.isAcceptable = false;
            String blurMsg = "Image appears blurry or out of focus. Please hold the phone steady and capture a clear view of the text.";
            result.qualityNotice = result.qualityNotice != null ? result.qualityNotice + " " + blurMsg : blurMsg;
        }

        return result;
    }

    /**
     * Preprocesses image for ML Kit Text Recognition:
     * 1. Auto-rotates using EXIF orientation metadata.
     * 2. Resizes low/high-res images to optimal OCR dimensions.
     * 3. Enhances contrast while preserving subtle text and avoiding foil clipping.
     * 4. Applies moderate edge sharpening.
     */
    public static Bitmap preprocessForOcr(Context context, Bitmap original, Uri imageUri) {
        if (original == null) return null;

        try {
            // 1. Correct orientation from EXIF if Uri available
            Bitmap rotated = handleRotation(context, original, imageUri);

            // 2. Resize to optimal OCR dimensions (1200 - 1800 px)
            Bitmap resized = resizeForOcr(rotated);

            // 3. Convert to Grayscale & Enhance Contrast gently
            Bitmap contrastEnhanced = applyGrayscaleAndContrast(resized);

            // 4. Sharpen text edges
            return sharpenBitmap(contrastEnhanced);

        } catch (Exception e) {
            e.printStackTrace();
            return original;
        }
    }

    private static Bitmap handleRotation(Context context, Bitmap bitmap, Uri imageUri) {
        if (imageUri == null || context == null) return bitmap;

        try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
            if (is == null) return bitmap;
            ExifInterface exif = new ExifInterface(is);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            int rotationDegrees = 0;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotationDegrees = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotationDegrees = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotationDegrees = 270;

            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private static Bitmap resizeForOcr(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();

        int maxDim = Math.max(width, height);

        // Upscale low-res images so ML Kit can read small characters
        if (maxDim < 900) {
            float scale = 1350f / maxDim;
            int newW = Math.round(width * scale);
            int newH = Math.round(height * scale);
            return Bitmap.createScaledBitmap(src, newW, newH, true);
        }

        // Downscale oversized images for performance & memory safety
        if (maxDim > 2000) {
            float scale = 1800f / maxDim;
            int newW = Math.round(width * scale);
            int newH = Math.round(height * scale);
            return Bitmap.createScaledBitmap(src, newW, newH, true);
        }

        return src;
    }

    private static Bitmap applyGrayscaleAndContrast(Bitmap src) {
        Bitmap processed = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(processed);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Convert to Grayscale
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);

        // Balanced contrast boost (1.3x contrast, -8 brightness)
        float contrast = 1.30f;
        float brightness = -8f;
        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });

        cm.postConcat(contrastMatrix);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);

        return processed;
    }

    private static Bitmap sharpenBitmap(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();

        int[] pixels = new int[width * height];
        src.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] result = new int[width * height];
        System.arraycopy(pixels, 0, result, 0, width * height);

        for (int y = 1; y < height - 1; y++) {
            int rowIdx = y * width;
            int topRowIdx = (y - 1) * width;
            int bottomRowIdx = (y + 1) * width;

            for (int x = 1; x < width - 1; x++) {
                int idx = rowIdx + x;

                int center = (pixels[idx] >> 8) & 0xFF;
                int top = (pixels[topRowIdx + x] >> 8) & 0xFF;
                int bottom = (pixels[bottomRowIdx + x] >> 8) & 0xFF;
                int left = (pixels[rowIdx + x - 1] >> 8) & 0xFF;
                int right = (pixels[rowIdx + x + 1] >> 8) & 0xFF;

                // Moderate sharpen formula: 5*center - (top + bottom + left + right)
                int sharpened = 5 * center - (top + bottom + left + right);
                if (sharpened < 0) sharpened = 0;
                else if (sharpened > 255) sharpened = 255;

                result[idx] = (0xFF << 24) | (sharpened << 16) | (sharpened << 8) | sharpened;
            }
        }

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        output.setPixels(result, 0, width, 0, 0, width, height);
        return output;
    }
}
