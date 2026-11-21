package com.nikon.metronome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/**
 * Rasterises the "A Thousand Li of Rivers and Mountains" lacquer painting into
 * a square texture that is mapped onto each face of the 3D metronome. All
 * artwork is drawn with Canvas paths so the app stays free of image assets.
 */
final class QianliTexture {

    private QianliTexture() {}

    static Bitmap create(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = size;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);

        // Ivory lacquer ground with a faint warm vertical sheen.
        p.setShader(new LinearGradient(0, 0, 0, s,
                new int[]{0xFFFFFDF5, 0xFFF7F2E4, 0xFFF1EAD8},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, s, s, p);
        p.setShader(null);

        // Ruyi clouds drifting in the upper ivory sky (behind nothing yet).
        drawCloud(c, p, s * 0.16f, s * 0.30f, s * 0.075f, 0x30);
        drawCloud(c, p, s * 0.82f, s * 0.22f, s * 0.060f, 0x28);
        drawCloud(c, p, s * 0.55f, s * 0.16f, s * 0.050f, 0x20);

        // Five stacked mountain ranges: far azurite to near malachite.
        // Each range is a filled gradient mass with a gold-foil ridge and
        // occasional pine silhouettes at its foot.
        float[] baseY    = {0.50f, 0.62f, 0.73f, 0.85f, 0.99f};
        float[] amp      = {0.13f, 0.15f, 0.16f, 0.17f, 0.16f};
        int[] topCol     = {0xFF6FB0C4, 0xFF3E93A6, 0xFF2E8C72, 0xFF2A7C56, 0xFF1F6446};
        int[] botCol     = {0xFF357E9C, 0xFF1E6A86, 0xFF176B52, 0xFF155E3E, 0xFF0F4A34};
        float[] phase    = {0.3f, 1.9f, 3.4f, 5.1f, 6.6f};
        int[] pineEvery  = {0, 0, 7, 6, 5};

        for (int i = 0; i < baseY.length; i++) {
            drawRange(c, p, s, baseY[i] * s, amp[i] * s, topCol[i], botCol[i],
                    phase[i], pineEvery[i], i);
        }

        // A calm river band at the very foot with gentle gilt ripples.
        drawRiver(c, p, s);

        // A few foreground clouds overlapping the ranges for depth.
        drawCloud(c, p, s * 0.30f, s * 0.62f, s * 0.045f, 0x24);
        drawCloud(c, p, s * 0.72f, s * 0.74f, s * 0.040f, 0x20);

        return bmp;
    }

    /** One mountain range: gradient body, gold ridge outline, optional pines. */
    private static void drawRange(Canvas c, Paint p, float s, float baseY, float amp,
                                  int top, int bot, float phase, int pineEvery, int layer) {
        float xL = -s * 0.05f, xR = s * 1.05f;
        float step = Math.max(2f, (xR - xL) / 90f);

        Path fill = new Path();
        Path ridge = new Path();
        // Extend every range down to the canvas foot so layers overlap with
        // no gaps; near ranges paint over the lower part of far ranges.
        fill.moveTo(xL, s);
        boolean first = true;
        int idx = 0;
        for (float x = xL; x <= xR + step; x += step) {
            float u = (x - xL) / step;
            float ridgeY = baseY - amp * wave(u * 0.13f, phase, layer);
            if (first) {
                ridge.moveTo(x, ridgeY);
                first = false;
            } else {
                ridge.lineTo(x, ridgeY);
            }
            fill.lineTo(x, ridgeY);
            if (pineEvery > 0 && idx % pineEvery == 0 && ridgeY < baseY - amp * 0.35f) {
                drawPine(c, p, x, ridgeY, s * (0.016f + 0.004f * (layer % 2)));
            }
            idx++;
        }
        fill.lineTo(xR + step, s);
        fill.close();

        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, baseY - amp, 0, baseY + amp * 1.4f,
                new int[]{top, bot}, null, Shader.TileMode.CLAMP));
        c.drawPath(fill, p);
        p.setShader(null);

        // Gold-foil ridge: a wider translucent halo plus a crisp gilt line.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, s * 0.007f));
        p.setColor(0x66D8B25C);
        c.drawPath(ridge, p);
        p.setStrokeWidth(Math.max(1f, s * 0.0035f));
        p.setColor(0xFFE8C877);
        c.drawPath(ridge, p);

        // Short diagonal "cun" strokes give the rock faces volume.
        p.setStrokeWidth(Math.max(0.8f, s * 0.0018f));
        p.setColor(0x2A0C2A20);
        for (float x = xL; x < xR; x += step * 3f) {
            float u = (x - xL) / step;
            float ry = baseY - amp * wave(u * 0.13f, phase, layer);
            c.drawLine(x, ry + amp * 0.05f, x + step * 1.2f, ry + amp * 0.5f, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** Sharpened multi-octave ridge so peaks read as mountains, not waves. */
    private static float wave(float x, float phase, int layer) {
        double a = Math.sin(x * 2.4 + phase) * 0.66
                + Math.sin(x * 5.1 + phase * 1.6) * 0.26
                + Math.sin(x * 9.3 + phase * 0.6) * 0.08;
        float v = 0.5f + 0.5f * (float) a;
        // Gentle rounding keeps the ranges lush and layered rather than spiky.
        return (float) Math.pow(v, 1.15 - layer * 0.04f);
    }

    /** Tiny pine tree silhouette. */
    private static void drawPine(Canvas c, Paint p, float x, float groundY, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF173F32);
        float tw = h * 0.10f;
        c.drawRect(x - tw * 0.5f, groundY - h * 0.45f,
                x + tw * 0.5f, groundY, p);
        // Three stacked triangular canopies.
        for (int k = 0; k < 3; k++) {
            float cy = groundY - h * (0.35f + k * 0.22f);
            float cw = h * (0.34f - k * 0.07f);
            Path tri = new Path();
            tri.moveTo(x, cy - h * 0.30f);
            tri.lineTo(x + cw, cy);
            tri.lineTo(x - cw, cy);
            tri.close();
            c.drawPath(tri, p);
        }
    }

    /** Gold ruyi (auspicious cloud) drawn as a stroked spiral scroll. */
    private static void drawCloud(Canvas c, Paint p, float x, float y, float s, int alpha) {
        Path cloud = new Path();
        cloud.moveTo(x - s, y);
        cloud.quadTo(x - s * 1.15f, y - s * 0.85f, x - s * 0.40f, y - s * 0.70f);
        cloud.quadTo(x - s * 0.20f, y - s * 1.25f, x + s * 0.35f, y - s * 0.80f);
        cloud.quadTo(x + s * 1.00f, y - s * 0.95f, x + s * 0.82f, y - s * 0.22f);
        cloud.quadTo(x + s * 1.30f, y + s * 0.05f, x + s * 0.55f, y + s * 0.16f);
        cloud.lineTo(x - s * 0.78f, y + s * 0.16f);
        cloud.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor((alpha << 24) | 0xFFFFF6DF);
        c.drawPath(cloud, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s * 0.06f));
        p.setColor((0xC0) << 24 | 0xFFC79A3E);
        c.drawPath(cloud, p);

        // Inner scroll.
        Path scroll = new Path();
        scroll.moveTo(x - s * 0.55f, y - s * 0.05f);
        scroll.quadTo(x - s * 0.35f, y - s * 0.45f, x - s * 0.05f, y - s * 0.18f);
        scroll.quadTo(x + s * 0.15f, y - s * 0.02f, x + s * 0.40f, y - s * 0.25f);
        p.setStrokeWidth(Math.max(0.8f, s * 0.045f));
        p.setColor((0xA0) << 24 | 0xFFD9AE55);
        c.drawPath(scroll, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** Gilt water ripples across the bottom of the panel. */
    private static void drawRiver(Canvas c, Paint p, float s) {
        Paint grad = new Paint(Paint.ANTI_ALIAS_FLAG);
        grad.setStyle(Paint.Style.FILL);
        grad.setShader(new LinearGradient(0, s * 0.90f, 0, s,
                new int[]{0x00EAF1E4, 0x30C9D9C6}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, s * 0.90f, s, s, grad);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.8f, s * 0.002f));
        p.setColor(0x50B08A3A);
        for (int i = 0; i < 5; i++) {
            float y = s * (0.918f + i * 0.017f);
            Path ripple = new Path();
            ripple.moveTo(s * 0.05f, y);
            ripple.quadTo(s * 0.30f, y - s * 0.010f, s * 0.52f, y);
            ripple.quadTo(s * 0.74f, y + s * 0.010f, s * 0.95f, y);
            c.drawPath(ripple, p);
        }
        p.setStyle(Paint.Style.FILL);
    }
}
