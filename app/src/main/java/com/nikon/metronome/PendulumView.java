package com.nikon.metronome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Hand drawn mechanical metronome: walnut pyramid case, ivory scale plate,
 * brass rod and weight. The rod is driven by the engine's continuous beat
 * phase, so the visual swing and the audible click stay locked together.
 */
public class PendulumView extends View {

    private static final int[] MARKS = {40, 60, 80, 100, 120, 144, 168, 200, 240};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bodyPath = new Path();
    private final Path platePath = new Path();

    private MetronomeEngine engine;
    private PendulumMode mode = PendulumMode.BOTTOM;
    private int bpm = 120;
    private Bitmap staticLayer;
    private boolean cacheDirty = true;
    private boolean wasRunning;
    private boolean settling;
    private float lastAngle;
    private float settleStartAngle;
    private long settleStartNanos;

    private float cx;
    private float bodyTop;
    private float bodyBottom;
    private float bodyH;
    private float bodyW;
    private float topHalf;
    private float botHalf;
    private float pivotY;
    private float rodLen;
    private float rodW;
    private float plateTop;
    private float plateBottom;
    private float plateTopHalf;
    private float plateBotHalf;

    public PendulumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
    }

    public void attach(MetronomeEngine engine) {
        this.engine = engine;
        invalidate();
    }

    /**
     * Restarts the draw loop when the engine starts or stops. onDraw() only
     * keeps scheduling frames while running/settling, so without this kick the
     * pendulum stays frozen until some other action invalidates the view.
     */
    public void onEngineStateChanged() {
        postInvalidateOnAnimation();
    }

    public void setSwingMode(PendulumMode mode) {
        if (mode == null || this.mode == mode) {
            return;
        }
        this.mode = mode;
        cacheDirty = true;
        invalidate();
    }

    public PendulumMode getSwingMode() {
        return mode;
    }

    public void setBpm(int bpm) {
        if (this.bpm == bpm) {
            return;
        }
        this.bpm = bpm;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cacheDirty = true;
        if (staticLayer != null) {
            staticLayer.recycle();
            staticLayer = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        computeGeometry(w, h);
        if (cacheDirty || staticLayer == null || staticLayer.getWidth() != w
                || staticLayer.getHeight() != h) {
            buildStaticLayer(w, h);
        }
        if (staticLayer != null) {
            canvas.drawBitmap(staticLayer, 0, 0, null);
        }

        boolean running = engine != null && engine.isRunning();
        double phase = running ? engine.getBeatPhase() : 0;
        float maxAngle = mode == PendulumMode.SPRING ? 12f
                : mode == PendulumMode.TOP ? 22f : 24f;

        if (running) {
            settling = false;
            lastAngle = maxAngle * (float) Math.cos(Math.PI * phase);
        } else if (wasRunning && mode != PendulumMode.SPRING) {
            settling = true;
            settleStartAngle = lastAngle;
            settleStartNanos = System.nanoTime();
        }
        wasRunning = running;

        float angle;
        if (running) {
            angle = lastAngle;
        } else if (settling) {
            float t = (System.nanoTime() - settleStartNanos) / 900_000_000f;
            if (t >= 1f) {
                settling = false;
                angle = 0f;
            } else {
                angle = settleStartAngle * (float) Math.exp(-3.2 * t)
                        * (float) Math.cos(11.0 * t);
            }
        } else {
            angle = 0f;
        }

        drawMovingParts(canvas, angle, phase, running);
        drawBpmPointer(canvas);

        if (running || settling) {
            postInvalidateOnAnimation();
        }
    }

    private void computeGeometry(float w, float h) {
        bodyH = Math.min(h * 0.93f, w * 1.55f);
        bodyW = bodyH * 0.60f;
        cx = w / 2f;
        bodyBottom = h * 0.965f;
        bodyTop = bodyBottom - bodyH;
        topHalf = bodyW * 0.17f;
        botHalf = bodyW * 0.5f;
        rodW = Math.max(2.5f, bodyW * 0.014f);
        rodLen = bodyH * 0.70f;

        pivotY = mode == PendulumMode.BOTTOM
                ? bodyBottom - bodyH * 0.16f
                : bodyTop + bodyH * 0.10f;

        plateTop = bodyTop + bodyH * 0.13f;
        plateBottom = bodyBottom - bodyH * 0.20f;
        plateTopHalf = topHalf * 0.50f;
        plateBotHalf = botHalf * 0.32f;
    }

    private void buildStaticLayer(int w, int h) {
        if (staticLayer != null) {
            staticLayer.recycle();
        }
        staticLayer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(staticLayer);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        // A soft sun halo instead of the old dark stage: the white card behind
        // this view stays visible through the transparent layer.
        paint.setShader(new RadialGradient(cx, h * 0.28f, Math.max(w, h) * 0.68f,
                0x33FFC94D, 0x00FFC94D, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Soft shadow under the case.
        paint.setColor(0x1F8A5A20);
        c.drawOval(cx - botHalf * 1.25f, bodyBottom - bodyH * 0.012f,
                cx + botHalf * 1.25f, bodyBottom + bodyH * 0.035f, paint);

        makeTrapezoid(bodyPath, cx, bodyTop, topHalf, bodyBottom, botHalf);
        paint.setShader(new LinearGradient(cx - botHalf, bodyTop, cx + botHalf, bodyBottom,
                new int[]{0xFFFFDC96, 0xFFF7BE6B, 0xFFE39A45, 0xFFFFC976},
                null, Shader.TileMode.CLAMP));
        c.drawPath(bodyPath, paint);
        paint.setShader(null);

        c.save();
        c.clipPath(bodyPath);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, bodyH * 0.0016f));
        for (int i = 0; i < 30; i++) {
            float y = bodyTop + bodyH * (i + 0.5f) / 30f;
            Path grain = new Path();
            grain.moveTo(cx - botHalf, y);
            for (int s = 1; s <= 12; s++) {
                float x = cx - botHalf + (2 * botHalf) * s / 12f;
                float yy = y + bodyW * 0.018f * (float) Math.sin(s * 0.85 + i * 1.7);
                grain.lineTo(x, yy);
            }
            paint.setColor(i % 3 == 0 ? 0x44FFFFFF : 0x18B9681A);
            c.drawPath(grain, paint);
        }
        c.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, bodyH * 0.004f));
        paint.setColor(0xFFF0A93B);
        c.drawPath(bodyPath, paint);
        paint.setStrokeWidth(1f);
        paint.setColor(0xAAFFFFFF);
        c.drawLine(cx - topHalf, bodyTop, cx - botHalf * 0.97f, bodyBottom, paint);

        // Base plinth.
        float baseH = bodyH * 0.045f;
        RectF base = new RectF(cx - botHalf * 1.06f, bodyBottom - baseH,
                cx + botHalf * 1.06f, bodyBottom + baseH * 0.15f);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, base.top, 0, base.bottom,
                0xFFF0A844, 0xFFD87A24, Shader.TileMode.CLAMP));
        c.drawRoundRect(base, baseH * 0.35f, baseH * 0.35f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFFFD77A);
        c.drawRoundRect(base, baseH * 0.35f, baseH * 0.35f, paint);

        drawScalePlate(c);
        drawNamePlate(c);
        cacheDirty = false;
    }

    private void drawScalePlate(Canvas c) {
        makeTrapezoid(platePath, cx, plateTop, plateTopHalf, plateBottom, plateBotHalf);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(cx - plateBotHalf, 0, cx + plateBotHalf, 0,
                new int[]{0xFFEAF6FF, 0xFFFFFFFF, 0xFFFFFBF0, 0xFFE4F2FA},
                null, Shader.TileMode.CLAMP));
        c.drawPath(platePath, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, bodyH * 0.003f));
        paint.setColor(0xFF8FD3E8);
        c.drawPath(platePath, paint);

        // Recess in which the rod travels.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x123A8FB0);
        c.drawRect(cx - rodW * 1.4f, plateTop, cx + rodW * 1.4f, plateBottom, paint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(Math.max(9f, bodyH * 0.021f));
        textPaint.setColor(0xFF3D6B8A);
        paint.setStrokeWidth(Math.max(1f, bodyH * 0.0018f));

        for (int mark : MARKS) {
            float t = markPosition(mark);
            float y = plateTop + (plateBottom - plateTop) * t;
            float half = plateTopHalf + (plateBotHalf - plateTopHalf) * t;
            paint.setColor(0xFF7FBBD4);
            c.drawLine(cx + half * 0.18f, y, cx + half * 0.82f, y, paint);
            c.drawText(String.valueOf(mark), cx - half * 0.16f,
                    y + textPaint.getTextSize() * 0.36f, textPaint);
        }
    }

    private void drawNamePlate(Canvas c) {
        float plateW = Math.min(botHalf * 1.05f, bodyW * 0.5f);
        float plateH = bodyH * 0.05f;
        float y = bodyBottom - bodyH * 0.085f;
        RectF rect = new RectF(cx - plateW / 2, y - plateH / 2,
                cx + plateW / 2, y + plateH / 2);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                new int[]{0xFFFFE79C, 0xFFFFB44D, 0xFFFF8A3D},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, plateH * 0.25f, plateH * 0.25f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xFFE4761F);
        c.drawRoundRect(rect, plateH * 0.25f, plateH * 0.25f, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(plateH * 0.48f);
        textPaint.setColor(0xFF7A3B0A);
        c.drawText("SUNNY BEAT", cx, y + plateH * 0.20f, textPaint);
    }

    private void drawMovingParts(Canvas c, float angle, double phase, boolean running) {
        float dir = mode == PendulumMode.BOTTOM ? -1f : 1f;
        c.save();
        if (mode == PendulumMode.SPRING) {
            // Pure vertical motion: no tilt, and the spring compresses down to
            // one third of its full length on every beat.
            float stretch = running
                    ? 1f / 3f + 2f / 3f * (float) Math.abs(Math.sin(Math.PI * phase))
                    : 0.88f;
            drawSpring(c, rodLen * stretch);
        } else {
            c.rotate(angle, cx, pivotY);
            drawBrassRod(c, dir);
        }
        c.restore();

        drawPivot(c);
    }

    private void drawBrassRod(Canvas c, float dir) {
        float yEnd = pivotY + dir * rodLen;
        float left = cx - rodW / 2;
        float right = cx + rodW / 2;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(left, 0, right, 0,
                new int[]{0xFFF0A22E, 0xFFFFF4C2, 0xFFFFC23D, 0xFFE07C1C},
                null, Shader.TileMode.CLAMP));
        c.drawRect(left, Math.min(pivotY, yEnd), right, Math.max(pivotY, yEnd), paint);
        paint.setShader(null);

        // Sliding weight: faster tempi put the weight closer to the pivot,
        // exactly like a real pendulum metronome.
        float t = (bpm - MetronomeEngine.MIN_BPM)
                / (float) (MetronomeEngine.MAX_BPM - MetronomeEngine.MIN_BPM);
        float weightAt = 0.90f - 0.44f * t;
        float wy = pivotY + dir * rodLen * weightAt;
        float halfW = bodyW * 0.055f;
        float halfH = bodyH * 0.023f;
        Path weight = new Path();
        weight.moveTo(cx - halfW * 0.72f, wy - halfH);
        weight.lineTo(cx + halfW * 0.72f, wy - halfH);
        weight.lineTo(cx + halfW, wy + halfH);
        weight.lineTo(cx - halfW, wy + halfH);
        weight.close();
        paint.setShader(new LinearGradient(cx - halfW, 0, cx + halfW, 0,
                new int[]{0xFFE58B26, 0xFFFFF0B0, 0xFFFFB22E, 0xFFD46E14},
                null, Shader.TileMode.CLAMP));
        c.drawPath(weight, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xFFB45A12);
        c.drawPath(weight, paint);
        paint.setColor(0xCCFFFFFF);
        c.drawLine(cx - halfW * 0.6f, wy, cx + halfW * 0.6f, wy, paint);

        // Tip of the rod.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFC94D);
        c.drawCircle(cx, yEnd, rodW * 1.5f, paint);
    }

    private void drawSpring(Canvas c, float length) {
        float coilW = bodyW * 0.085f;
        float turns = 11f;
        Path spring = new Path();
        int points = 180;
        for (int i = 0; i <= points; i++) {
            float t = i / (float) points;
            float x = cx + coilW * (float) Math.sin(t * turns * 2 * Math.PI);
            float y = pivotY + length * t;
            if (i == 0) {
                spring.moveTo(x, y);
            } else {
                spring.lineTo(x, y);
            }
        }
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(3f, bodyH * 0.010f));
        paint.setColor(0xFF2EC4B6);
        c.drawPath(spring, paint);
        paint.setStrokeWidth(Math.max(1f, bodyH * 0.003f));
        paint.setColor(0xCCEFFFFB);
        c.drawPath(spring, paint);

        float bobR = bodyW * 0.072f;
        float by = pivotY + length + bobR;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx - bobR * 0.35f, by - bobR * 0.4f,
                bobR * 1.5f, 0xFFFFF3B0, 0xFFFF8A3D, Shader.TileMode.CLAMP));
        c.drawCircle(cx, by, bobR, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xFFE06A1A);
        c.drawCircle(cx, by, bobR, paint);
    }

    private void drawPivot(Canvas c) {
        float r = bodyW * 0.038f;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx - r * 0.35f, pivotY - r * 0.4f, r * 1.6f,
                0xFFFFFFFF, 0xFF2EC4B6, Shader.TileMode.CLAMP));
        c.drawCircle(cx, pivotY, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xFF159C90);
        c.drawCircle(cx, pivotY, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x88159C90);
        c.drawCircle(cx, pivotY, r * 0.25f, paint);
    }

    private void drawBpmPointer(Canvas c) {
        float t = markPosition(Math.max(40, Math.min(240, bpm)));
        float y = plateTop + (plateBottom - plateTop) * t;
        float half = plateTopHalf + (plateBotHalf - plateTopHalf) * t;
        float x = cx + half * 0.95f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFF5A5F);
        Path pointer = new Path();
        pointer.moveTo(x, y);
        pointer.lineTo(x + bodyW * 0.055f, y - bodyH * 0.011f);
        pointer.lineTo(x + bodyW * 0.055f, y + bodyH * 0.011f);
        pointer.close();
        c.drawPath(pointer, paint);
    }

    private static float markPosition(int bpm) {
        double value = Math.max(40, Math.min(240, bpm));
        return (float) (Math.log(value / 40.0) / Math.log(240.0 / 40.0));
    }

    private static void makeTrapezoid(Path path, float cx, float top, float topHalf,
                                      float bottom, float bottomHalf) {
        path.reset();
        path.moveTo(cx - topHalf, top);
        path.lineTo(cx + topHalf, top);
        path.lineTo(cx + bottomHalf, bottom);
        path.lineTo(cx - bottomHalf, bottom);
        path.close();
    }
}
