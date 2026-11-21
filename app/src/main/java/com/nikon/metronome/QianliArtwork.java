package com.nikon.metronome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Hand-drawn "A Thousand Li of Rivers and Mountains" metronome artwork.
 *
 * The whole object is a truncated four-sided pyramid rendered with a simple
 * painter's algorithm: three visible faces are shaded with ivory-lacquer
 * gradients, then azurite / malachite mountain layers are clipped into each
 * face and outlined with gold foil. A brass rod, tempo dial and gilt feet sit
 * on top. No bitmaps and no external libraries - everything is Canvas paths.
 */
final class QianliArtwork {

    // Gold leaf palette
    private static final int GOLD_HI = 0xFFFFE9A8;
    private static final int GOLD = 0xFFD9AE55;
    private static final int GOLD_MID = 0xFFBB8A2C;
    private static final int GOLD_DK = 0xFF875C15;
    private static final int GOLD_LINE = 0xFFC9A24A;

    // Ivory lacquer palette
    private static final int IVORY_HI = 0xFFFFFDF4;
    private static final int IVORY = 0xFFF5F0E2;
    private static final int IVORY_MID = 0xFFE6DEC9;
    private static final int IVORY_DK = 0xFFCFC5AC;

    // Azurite (shi-qing) and malachite (shi-lü)
    private static final int AZURITE_1 = 0xFF215C80;
    private static final int AZURITE_2 = 0xFF3B86A6;
    private static final int MALACHITE_1 = 0xFF2A7459;
    private static final int MALACHITE_2 = 0xFF57A184;
    private static final int MALACHITE_3 = 0xFF33896C;
    private static final int INK = 0xFF1E3438;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bodyPath = new Path();
    private final Path facePath = new Path();

    // Geometry shared by the static body and the swinging rod.
    private float cx;
    private float bodyTop;
    private float bodyBottom;
    private float bodyH;
    private float baseHalf;
    private float topHalf;
    private float depth;
    private float depthY;
    private float pivotY;
    private float rodLen;
    private float scaleTop;
    private float scaleBottom;
    private float scaleHalfTop;
    private float scaleHalfBottom;
    private float dialCy;
    private float dialR;
    private float w;
    private float h;

    QianliArtwork() {
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Draws the static lacquered pyramid, its landscape and fixed fittings. */
    void drawStatic(Canvas c, int width, int height) {
        w = width;
        h = height;
        computeGeometry();

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        drawHalo(c);
        drawFootShadow(c);
        drawFeet(c);

        // Painter's order: receding side first, lit front face last.
        drawSideFace(c);
        drawTopFace(c);
        drawFrontFace(c);

        drawCrown(c);
        drawTempoScale(c);
        drawDialFace(c);
    }

    /** Draws the swinging brass rod, its weight and the tempo pointer. */
    void drawMoving(Canvas c, float angleDeg, int bpm) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        float angle = angleDeg * 0.7f;
        c.save();
        c.rotate(angle, cx, pivotY);
        drawRod(c);
        drawRodWeight(c, bpm);
        c.restore();

        drawPivot(c);
        drawDialNeedle(c, angle);
        drawTempoPointer(c, bpm);
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private void computeGeometry() {
        // The front face is nudged right so the receding left face stays visible.
        bodyH = Math.min(h * 0.88f, w * 1.30f);
        bodyBottom = h * 0.945f;
        bodyTop = bodyBottom - bodyH;
        baseHalf = bodyH * 0.355f;
        topHalf = baseHalf * 0.13f;
        depth = baseHalf * 0.42f;
        depthY = bodyH * 0.05f;
        cx = w / 2f + depth * 0.5f;

        pivotY = bodyBottom - bodyH * 0.135f;
        rodLen = bodyH * 0.66f;

        scaleTop = bodyTop + bodyH * 0.085f;
        scaleBottom = bodyBottom - bodyH * 0.235f;
        scaleHalfTop = topHalf * 1.05f;
        scaleHalfBottom = baseHalf * 0.115f;

        dialCy = bodyBottom - bodyH * 0.052f;
        dialR = bodyH * 0.056f;
    }

    // ------------------------------------------------------------------
    // Backdrop and base
    // ------------------------------------------------------------------

    private void drawHalo(Canvas c) {
        paint.setShader(new RadialGradient(cx, h * 0.34f, Math.max(w, h) * 0.72f,
                new int[]{0x26FFF3D6, 0x08FFF3D6, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private void drawFootShadow(Canvas c) {
        paint.setColor(0x33413A28);
        c.drawOval(cx - baseHalf * 1.12f, bodyBottom - bodyH * 0.008f,
                cx + baseHalf * 1.12f, bodyBottom + bodyH * 0.04f, paint);
        paint.setAlpha(0xFF);
    }

    private void drawFeet(Canvas c) {
        float r = Math.max(3f, bodyH * 0.021f);
        float fy = bodyBottom + bodyH * 0.012f;
        float[] xs = {cx - baseHalf * 0.82f, cx + baseHalf * 0.82f,
                cx - baseHalf * 0.82f - depth * 0.85f};
        float[] ys = {fy, fy, fy - depthY};
        for (int i = 0; i < xs.length; i++) {
            paint.setShader(new RadialGradient(xs[i] - r * 0.35f, ys[i] - r * 0.35f,
                    r * 1.6f, new int[]{GOLD_HI, GOLD, GOLD_DK},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(xs[i], ys[i], r, paint);
            paint.setShader(null);
        }
    }

    // ------------------------------------------------------------------
    // The lacquered pyramid faces
    // ------------------------------------------------------------------

    private void drawSideFace(Canvas c) {
        // Receding left facet: front-left edge shares the front face; the back
        // edge is pushed up and left, so a real corner is visible in profile.
        facePath.reset();
        facePath.moveTo(cx - topHalf, bodyTop);
        facePath.lineTo(cx - topHalf - depth, bodyTop - depthY);
        facePath.lineTo(cx - baseHalf - depth, bodyBottom - depthY);
        facePath.lineTo(cx - baseHalf, bodyBottom);
        facePath.close();

        paint.setShader(new LinearGradient(cx - baseHalf - depth, 0, cx, 0,
                new int[]{0xFFB9AE94, 0xFFD3C8AC, 0xFFE6DCC2},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(facePath, paint);
        paint.setShader(null);

        c.save();
        c.clipPath(facePath);
        drawMountainRange(c, -1, true);
        c.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, bodyH * 0.004f));
        paint.setColor(GOLD_MID);
        c.drawPath(facePath, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawTopFace(Canvas c) {
        facePath.reset();
        facePath.moveTo(cx - topHalf, bodyTop);
        facePath.lineTo(cx - topHalf - depth, bodyTop - depthY);
        facePath.lineTo(cx + topHalf - depth, bodyTop - depthY);
        facePath.lineTo(cx + topHalf, bodyTop);
        facePath.close();
        paint.setShader(new LinearGradient(0, bodyTop - depthY, 0, bodyTop,
                new int[]{IVORY_HI, IVORY}, null, Shader.TileMode.CLAMP));
        c.drawPath(facePath, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.2f, bodyH * 0.003f));
        paint.setColor(GOLD_LINE);
        c.drawPath(facePath, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFrontFace(Canvas c) {
        bodyPath.reset();
        bodyPath.moveTo(cx - topHalf, bodyTop);
        bodyPath.lineTo(cx + topHalf, bodyTop);
        bodyPath.lineTo(cx + baseHalf, bodyBottom);
        bodyPath.lineTo(cx - baseHalf, bodyBottom);
        bodyPath.close();

        paint.setShader(new LinearGradient(cx - baseHalf, 0, cx + baseHalf, 0,
                new int[]{0xFFE7DFCC, IVORY_HI, 0xFFFBF6E9, IVORY_MID},
                new float[]{0f, 0.32f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(bodyPath, paint);
        paint.setShader(null);

        c.save();
        c.clipPath(bodyPath);
        drawMountainRange(c, 0, false);
        drawClouds(c);
        c.restore();

        // Gilded outline and a soft light edge down the left arris.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, bodyH * 0.006f));
        paint.setShader(new LinearGradient(0, bodyTop, 0, bodyBottom,
                new int[]{GOLD_HI, GOLD, GOLD_MID}, null, Shader.TileMode.CLAMP));
        c.drawPath(bodyPath, paint);
        paint.setShader(null);
        paint.setStrokeWidth(Math.max(1f, bodyH * 0.002f));
        paint.setColor(0xAAFFFFFF);
        c.drawLine(cx - topHalf, bodyTop, cx - baseHalf, bodyBottom, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------
    // Layered azurite / malachite mountains with gold-foil ridges
    // ------------------------------------------------------------------

    private void drawMountainRange(Canvas c, int side, boolean dark) {
        float x0 = cx - baseHalf * 1.2f;
        float x1 = cx + baseHalf * 1.2f;
        float baseY = bodyBottom - bodyH * 0.012f;
        float span = bodyH * 0.52f;
        float offset = side * 0.7f;

        // Far range - azurite, then middle and near ranges - malachite.
        mountainLayer(c, x0, x1, baseY - span * 0.30f, span * 0.42f,
                AZURITE_2, AZURITE_1, 0.4f + offset, dark);
        mountainLayer(c, x0, x1, baseY - span * 0.08f, span * 0.40f,
                MALACHITE_2, MALACHITE_1, 2.1f + offset, dark);
        mountainLayer(c, x0, x1, baseY + span * 0.10f, span * 0.38f,
                MALACHITE_3, MALACHITE_1, 3.9f + offset, dark);
    }

    private void mountainLayer(Canvas c, float x0, float x1, float baseY,
                               float height, int cTop, int cBot,
                               float phase, boolean dark) {
        float step = Math.max(4f, (x1 - x0) / 26f);
        Path fill = new Path();
        Path ridge = new Path();
        fill.moveTo(x0, baseY + height);
        boolean first = true;
        for (float x = x0; x <= x1 + step; x += step) {
            float t = (x - x0) / step;
            double s1 = Math.sin(phase + t * 0.61f) * 0.66;
            double s2 = Math.sin(phase * 1.83f + t * 0.27f) * 0.34;
            float wave = 0.42f + 0.58f * (float) Math.abs(s1 + s2);
            float y = baseY - height * wave;
            if (first) {
                ridge.moveTo(x, y);
                first = false;
            } else {
                ridge.lineTo(x, y);
            }
            fill.lineTo(x, y);
        }
        fill.lineTo(x1 + step, baseY + height);
        fill.close();

        int top = dark ? shade(cTop, 0.74f) : cTop;
        int bot = dark ? shade(cBot, 0.74f) : cBot;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, baseY - height, 0, baseY + height * 0.35f,
                new int[]{top, bot}, null, Shader.TileMode.CLAMP));
        c.drawPath(fill, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(0.8f, bodyH * 0.0026f));
        paint.setColor(dark ? 0x8A9A7C46 : GOLD_LINE);
        c.drawPath(ridge, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawClouds(Canvas c) {
        float upper = bodyTop + bodyH * 0.20f;
        float lower = bodyTop + bodyH * 0.40f;
        drawCloud(c, cx - baseHalf * 0.46f, upper, bodyH * 0.030f, 0xC0);
        drawCloud(c, cx + baseHalf * 0.50f, upper + bodyH * 0.075f,
                bodyH * 0.026f, 0xB0);
        drawCloud(c, cx - baseHalf * 0.20f, lower, bodyH * 0.022f, 0xA8);
        drawCloud(c, cx + baseHalf * 0.30f, lower + bodyH * 0.05f,
                bodyH * 0.020f, 0x98);
    }

    private void drawCloud(Canvas c, float x, float y, float s, int alpha) {
        Path p = new Path();
        p.moveTo(x - s, y);
        p.quadTo(x - s * 1.12f, y - s * 0.78f, x - s * 0.34f, y - s * 0.60f);
        p.quadTo(x - s * 0.12f, y - s * 1.16f, x + s * 0.36f, y - s * 0.74f);
        p.quadTo(x + s * 0.96f, y - s * 0.92f, x + s * 0.80f, y - s * 0.20f);
        p.quadTo(x + s * 1.26f, y + s * 0.02f, x + s * 0.58f, y + s * 0.14f);
        p.lineTo(x - s * 0.74f, y + s * 0.14f);
        p.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor((alpha << 24) | 0xFFF7F1DD);
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(0.8f, bodyH * 0.0022f));
        paint.setColor((alpha << 24) | 0xFFC9A24A);
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Gilded ruyi ornament crowning the truncated apex. */
    private void drawCrown(Canvas c) {
        float y = bodyTop - depthY * 0.45f;
        float s = topHalf * 1.15f;
        Path p = new Path();
        p.moveTo(cx - s, y);
        p.quadTo(cx - s * 0.9f, y - s * 0.85f, cx - s * 0.3f, y - s * 0.5f);
        p.quadTo(cx, y - s * 1.35f, cx + s * 0.3f, y - s * 0.5f);
        p.quadTo(cx + s * 0.9f, y - s * 0.85f, cx + s, y);
        p.quadTo(cx, y + s * 0.55f, cx - s, y);
        p.close();
        paint.setShader(new LinearGradient(cx - s, y - s, cx + s, y + s,
                new int[]{GOLD_HI, GOLD, GOLD_MID}, null, Shader.TileMode.CLAMP));
        c.drawPath(p, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(0.8f, bodyH * 0.002f));
        paint.setColor(GOLD_DK);
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, y - s * 0.28f, s * 0.16f, paintFor(GOLD_HI));
        paint.setShader(null);
    }

    // ------------------------------------------------------------------
    // Tempo scale, brass rod, weight, pivot and dial
    // ------------------------------------------------------------------

    private void drawTempoScale(Canvas c) {
        Path slot = new Path();
        slot.moveTo(cx - scaleHalfTop, scaleTop);
        slot.lineTo(cx + scaleHalfTop, scaleTop);
        slot.lineTo(cx + scaleHalfBottom, scaleBottom);
        slot.lineTo(cx - scaleHalfBottom, scaleBottom);
        slot.close();

        // Dark recessed channel the rod travels in.
        paint.setShader(new LinearGradient(cx - scaleHalfBottom, 0, cx + scaleHalfBottom, 0,
                new int[]{0xFF101D20, 0xFF2C4043, 0xFF152326},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(slot, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, bodyH * 0.0028f));
        paint.setColor(GOLD_MID);
        c.drawPath(slot, paint);
        paint.setStyle(Paint.Style.FILL);

        int[] marks = {40, 60, 80, 100, 120, 144, 168, 200, 240};
        textPaint.setTextSize(Math.max(7f, bodyH * 0.015f));
        textPaint.setColor(GOLD_HI);
        for (int mark : marks) {
            float t = markPosition(mark);
            float y = scaleTop + (scaleBottom - scaleTop) * t;
            float half = scaleHalfTop + (scaleHalfBottom - scaleHalfTop) * t;
            paint.setStrokeWidth(Math.max(0.8f, bodyH * 0.0018f));
            paint.setColor(GOLD);
            c.drawLine(cx + half * 0.18f, y, cx + half * 0.86f, y, paint);
            c.drawText(String.valueOf(mark), cx - half * 0.46f,
                    y + textPaint.getTextSize() * 0.34f, textPaint);
        }
    }

    private void drawRod(Canvas c) {
        float halfW = Math.max(1.6f, bodyH * 0.006f);
        float yEnd = pivotY - rodLen;
        paint.setShader(new LinearGradient(cx - halfW, 0, cx + halfW, 0,
                new int[]{GOLD_DK, GOLD_HI, GOLD, GOLD_MID},
                new float[]{0f, 0.32f, 0.66f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(cx - halfW, yEnd, cx + halfW, pivotY, paint);
        paint.setShader(null);
    }

    private void drawRodWeight(Canvas c, int bpm) {
        float t = (bpm - 40f) / 200f;
        float at = 0.92f - 0.46f * t;
        float wy = pivotY - rodLen * at;
        float hw = Math.max(4f, bodyH * 0.022f);
        float hh = Math.max(3f, bodyH * 0.013f);

        Path gem = new Path();
        gem.moveTo(cx, wy - hh * 1.5f);
        gem.lineTo(cx + hw, wy);
        gem.lineTo(cx, wy + hh * 1.5f);
        gem.lineTo(cx - hw, wy);
        gem.close();
        paint.setShader(new LinearGradient(cx - hw, 0, cx + hw, 0,
                new int[]{GOLD_MID, GOLD_HI, GOLD, GOLD_DK}, null, Shader.TileMode.CLAMP));
        c.drawPath(gem, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(0.8f, bodyH * 0.0016f));
        paint.setColor(GOLD_DK);
        c.drawPath(gem, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPivot(Canvas c) {
        float r = Math.max(4f, bodyH * 0.024f);
        paint.setShader(new RadialGradient(cx - r * 0.35f, pivotY - r * 0.35f, r * 1.7f,
                new int[]{GOLD_HI, GOLD, GOLD_DK}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, pivotY, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, pivotY, r * 0.28f, paintFor(GOLD_DK));
        paint.setShader(null);
    }

    private void drawDialFace(Canvas c) {
        // Outer gilt bezel.
        paint.setShader(new RadialGradient(cx - dialR * 0.4f, dialCy - dialR * 0.4f,
                dialR * 1.8f, new int[]{GOLD_HI, GOLD, GOLD_DK}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, dialCy, dialR, paint);
        paint.setShader(null);
        // Ivory dial plate.
        c.drawCircle(cx, dialCy, dialR * 0.82f, paintFor(IVORY_HI));
        paint.setShader(null);

        int ticks = 12;
        for (int i = 0; i < ticks; i++) {
            double a = (Math.PI * 2 * i) / ticks - Math.PI / 2;
            float inner = dialR * (i % 3 == 0 ? 0.58f : 0.66f);
            float outer = dialR * 0.76f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(i % 3 == 0 ? Math.max(1.4f, dialR * 0.06f)
                    : Math.max(0.8f, dialR * 0.03f));
            paint.setColor(i % 3 == 0 ? INK : GOLD_MID);
            c.drawLine(cx + (float) Math.cos(a) * inner,
                    dialCy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * outer,
                    dialCy + (float) Math.sin(a) * outer, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(cx - dialR * 0.5f, dialCy,
                cx + dialR * 0.5f, dialCy,
                new int[]{GOLD_MID, GOLD_HI, GOLD}, null, Shader.TileMode.CLAMP));
        c.drawRect(cx - dialR * 0.5f, dialCy - dialR * 0.12f,
                cx + dialR * 0.5f, dialCy + dialR * 0.1f, paint);
        paint.setShader(null);
    }

    private void drawDialNeedle(Canvas c, float angle) {
        float len = dialR * 0.72f;
        float a = (float) Math.toRadians(angle * 1.2f - 90f);
        float nx = cx + (float) Math.cos(a) * len;
        float ny = dialCy + (float) Math.sin(a) * len;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.6f, dialR * 0.08f));
        paint.setColor(INK);
        c.drawLine(cx, dialCy, nx, ny, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, dialCy, Math.max(2f, dialR * 0.11f), paintFor(GOLD_DK));
        paint.setShader(null);
    }

    private void drawTempoPointer(Canvas c, int bpm) {
        float t = markPosition(bpm);
        float y = scaleTop + (scaleBottom - scaleTop) * t;
        float half = scaleHalfTop + (scaleHalfBottom - scaleHalfTop) * t;
        float x = cx + half * 0.9f;
        float s = Math.max(4f, bodyH * 0.014f);
        Path p = new Path();
        p.moveTo(x + s, y);
        p.lineTo(x - s * 0.6f, y - s * 0.7f);
        p.lineTo(x - s * 0.6f, y + s * 0.7f);
        p.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(INK);
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Paint paintFor(int color) {
        paint.setShader(null);
        paint.setColor(color);
        return paint;
    }

    private static int shade(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a, r, g, b);
    }

    private static float markPosition(int bpm) {
        double value = Math.max(40, Math.min(240, bpm));
        return (float) (Math.log(value / 40.0) / Math.log(240.0 / 40.0));
    }
}
