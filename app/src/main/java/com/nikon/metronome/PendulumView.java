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
import android.util.SparseIntArray;
import android.view.View;

/**
 * Hand drawn mechanical metronome with two palettes: warm maple for the
 * sunny style, celadon / ink / cinnabar for the Song-dynasty style. The rod
 * is driven by the engine's continuous beat phase, so the visual swing and
 * the audible click stay locked together.
 */
public class PendulumView extends View {

    private static final int[] MARKS = {40, 60, 80, 100, 120, 144, 168, 200, 240};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bodyPath = new Path();
    private final Path platePath = new Path();
    private final QianliArtwork qianliArt = new QianliArtwork();

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
    public static final int STYLE_SUNNY = 0;
    public static final int STYLE_SONG = 1;
    public static final int STYLE_AURORA = 2;
    public static final int STYLE_ROSE = 3;
    public static final int STYLE_NEON = 4;
    public static final int STYLE_TAIJI = 5;
    public static final int STYLE_QIANLI = 6;
    public static final int STYLE_COUNT = 7;

    private static final int TAIJI_INK = 0xFF232E2A;
    private static final int TAIJI_RICE = 0xFFF7F4EA;

    private int styleIndex = STYLE_SUNNY;
    private int userStyle = STYLE_SUNNY;
    private float taijiAngle;
    private boolean taijiClockwise = true;

    private static final String[] STYLE_LABELS = {
            "SUNNY BEAT", "宋韵", "AURORA", "ROSE GOLD", "NEON", "太极", "千里江山"
    };

    private static final SparseIntArray SONG_COLORS = new SparseIntArray();
    private static final SparseIntArray AURORA_COLORS;
    private static final SparseIntArray ROSE_COLORS;
    private static final SparseIntArray NEON_COLORS;
    private static final SparseIntArray TAIJI_COLORS;

    static {
        // Warm sunny colour -> Song palette: warm rice paper, soft celadon,
        // quiet ink-green and restrained cinnabar / antique gold.
        SONG_COLORS.put(0x33FFC94D, 0x245F5A);
        SONG_COLORS.put(0x00FFC94D, 0x005F5A);
        SONG_COLORS.put(0x1F8A5A20, 0x1F3E544B);
        SONG_COLORS.put(0xFFFFDC96, 0xFFE6F0E9);
        SONG_COLORS.put(0xFFF7BE6B, 0xFFC6DCCF);
        SONG_COLORS.put(0xFFE39A45, 0xFFA8C8B8);
        SONG_COLORS.put(0xFFFFC976, 0xFFE6F0E9);
        SONG_COLORS.put(0x18B9681A, 0x183E544B);
        SONG_COLORS.put(0xFFF0A93B, 0xFF789C8C);
        SONG_COLORS.put(0xFFF0A844, 0xFFC6DCCF);
        SONG_COLORS.put(0xFFD87A24, 0xFF9CBCAB);
        SONG_COLORS.put(0xFFFFD77A, 0xFF789C8C);
        SONG_COLORS.put(0xFFEAF6FF, 0xFFFFFDF5);
        SONG_COLORS.put(0xFFFFFFFF, 0xFFFBF9F1);
        SONG_COLORS.put(0xFFFFFBF0, 0xFFF4EFE2);
        SONG_COLORS.put(0xFFE4F2FA, 0xFFE9EFE6);
        SONG_COLORS.put(0xFF8FD3E8, 0xFF789C8C);
        SONG_COLORS.put(0x123A8FB0, 0x123E544B);
        SONG_COLORS.put(0xFF3D6B8A, 0xFF3E544B);
        SONG_COLORS.put(0xFF7FBBD4, 0xFF789C8C);
        SONG_COLORS.put(0xFFFFE79C, 0xFFB4573E);
        SONG_COLORS.put(0xFFFFB44D, 0xFFA44A34);
        SONG_COLORS.put(0xFFFF8A3D, 0xFF8E3B2B);
        SONG_COLORS.put(0xFFE4761F, 0xFF8E3B2B);
        SONG_COLORS.put(0xFF7A3B0A, 0xFFFFFDF5);
        SONG_COLORS.put(0xFFF0A22E, 0xFF3E544B);
        SONG_COLORS.put(0xFFFFF4C2, 0xFFDCE9E0);
        SONG_COLORS.put(0xFFFFC23D, 0xFFA8C8B8);
        SONG_COLORS.put(0xFFE07C1C, 0xFF3E544B);
        SONG_COLORS.put(0xFFE58B26, 0xFFC6DCCF);
        SONG_COLORS.put(0xFFFFF0B0, 0xFFE6F0E9);
        SONG_COLORS.put(0xFFFFB22E, 0xFFA8C8B8);
        SONG_COLORS.put(0xFFD46E14, 0xFF789C8C);
        SONG_COLORS.put(0xFFB45A12, 0xFF3E544B);
        SONG_COLORS.put(0xFFFFC94D, 0xFFA8C8B8);
        SONG_COLORS.put(0xFF2EC4B6, 0xFF789C8C);
        SONG_COLORS.put(0xCCEFFFFB, 0xCCF3F1E8);
        SONG_COLORS.put(0xFFFFF3B0, 0xFFE6F0E9);
        SONG_COLORS.put(0xFFE06A1A, 0xFF3E544B);
        SONG_COLORS.put(0xFF159C90, 0xFF3E544B);
        SONG_COLORS.put(0x88159C90, 0x883E544B);
        SONG_COLORS.put(0xFFFF5A5F, 0xFFB4573E);
        SONG_COLORS.put(0x44FFFFFF, 0x44FFFDF5);
        SONG_COLORS.put(0xAAFFFFFF, 0xAAFFFDF5);
        SONG_COLORS.put(0xCCFFFFFF, 0xCCFFFDF5);

        // Derived styles keep the sunny palette's internal relationships by
        // rotating the whole hue, so every variant stays harmonious. NEON also
        // swaps the translucent structural colours for a dark synthwave base.
        AURORA_COLORS = buildHueRotatedMap(120, 1.10f, 1.02f, false);
        ROSE_COLORS = buildHueRotatedMap(-20, 0.85f, 1.06f, false);
        NEON_COLORS = buildHueRotatedMap(200, 1.30f, 0.95f, true);
        TAIJI_COLORS = buildInkMap();
    }

    /** Ink-wash reinterpretation: rice paper, pale jade and deep ink. */
    private static SparseIntArray buildInkMap() {
        SparseIntArray map = new SparseIntArray();
        for (int i = 0; i < SONG_COLORS.size(); i++) {
            int key = SONG_COLORS.keyAt(i);
            map.put(key, inkTone(key));
        }
        return map;
    }

    private static int inkTone(int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
        int rgb;
        if (lum > 0.80f) rgb = 0xFBF8F0;
        else if (lum > 0.65f) rgb = 0xE3E8DE;
        else if (lum > 0.50f) rgb = 0xB9CFC2;
        else if (lum > 0.35f) rgb = 0x6E8F82;
        else if (lum > 0.18f) rgb = 0x3C4A44;
        else rgb = 0x232E2A;
        return (a << 24) | rgb;
    }

    private int tone(int sunColor) {
        switch (styleIndex) {
            case STYLE_SONG:
                return SONG_COLORS.get(sunColor, sunColor);
            case STYLE_AURORA:
                return AURORA_COLORS.get(sunColor, sunColor);
            case STYLE_ROSE:
                return ROSE_COLORS.get(sunColor, sunColor);
            case STYLE_NEON:
                return NEON_COLORS.get(sunColor, sunColor);
            case STYLE_TAIJI:
                return TAIJI_COLORS.get(sunColor, sunColor);
            default:
                return sunColor;
        }
    }

    public void setStyle(int style) {
        if (style < 0 || style >= STYLE_COUNT) {
            return;
        }
        styleIndex = style;
        if (style != STYLE_SONG) {
            userStyle = style;
        }
        cacheDirty = true;
        invalidate();
    }

    public int getStyle() {
        return styleIndex;
    }

    public void setSongStyle(boolean song) {
        setStyle(song ? STYLE_SONG : (userStyle == STYLE_SONG ? STYLE_SUNNY : userStyle));
    }

    private static SparseIntArray buildHueRotatedMap(int hueShift, float satMul,
            float lightMul, boolean darkenBgs) {
        SparseIntArray map = new SparseIntArray();
        for (int i = 0; i < SONG_COLORS.size(); i++) {
            int key = SONG_COLORS.keyAt(i);
            int a = (key >>> 24) & 0xFF;
            if (darkenBgs && a <= 0x3F) {
                int na = Math.max(a, 0x22);
                map.put(key, (na << 24) | 0x0B1020);
                continue;
            }
            map.put(key, hslTransform(key, hueShift, satMul, lightMul));
        }
        return map;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int hslTransform(int color, int hueShift, float satMul, float lightMul) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float[] hsl = rgbToHsl(r, g, b);
        float h = (hsl[0] + hueShift) % 360f;
        if (h < 0) h += 360f;
        float s = clamp01(hsl[1] * satMul);
        float l = clamp01(hsl[2] * lightMul);
        int[] rgb = hslToRgb(h / 360f, s, l);
        return (a << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    private static float[] rgbToHsl(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h, s, l = (max + min) / 2f;
        if (max == min) {
            h = 0f; s = 0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == rf) h = (gf - bf) / d + (gf < bf ? 6f : 0f);
            else if (max == gf) h = (bf - rf) / d + 2f;
            else h = (rf - gf) / d + 4f;
            h /= 6f;
        }
        return new float[]{h * 360f, s, l};
    }

    private static int[] hslToRgb(float h, float s, float l) {
        float[] rgb = new float[3];
        if (s == 0) {
            rgb[0] = rgb[1] = rgb[2] = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            rgb[0] = hueToChannel(p, q, h + 1f / 3f);
            rgb[1] = hueToChannel(p, q, h);
            rgb[2] = hueToChannel(p, q, h - 1f / 3f);
        }
        return new int[]{(int) (rgb[0] * 255 + 0.5f),
                (int) (rgb[1] * 255 + 0.5f), (int) (rgb[2] * 255 + 0.5f)};
    }

    private static float hueToChannel(float p, float q, float t) {
        if (t < 0) t += 1f;
        if (t > 1) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

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
    private float taijiCy;
    private float taijiDiscR;

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

    public void setTaijiClockwise(boolean clockwise) {
        if (taijiClockwise == clockwise) {
            return;
        }
        taijiClockwise = clockwise;
        taijiAngle = 0f;
        invalidate();
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

        boolean running = engine != null && engine.isRunning();
        double phase = running ? engine.getBeatPhase() : 0;

        if (styleIndex == STYLE_TAIJI) {
            drawTaijiMoving(canvas, phase, running);
            wasRunning = running;
            if (running) {
                postInvalidateOnAnimation();
            }
            return;
        }

        if (cacheDirty || staticLayer == null || staticLayer.getWidth() != w
                || staticLayer.getHeight() != h) {
            buildStaticLayer(w, h);
        }
        if (staticLayer != null) {
            canvas.drawBitmap(staticLayer, 0, 0, null);
        }

        float maxAngle = mode == PendulumMode.SPRING ? 12f
                : mode == PendulumMode.TOP ? 22f : 24f;

        if (running) {
            settling = false;
            // The click fires at an integer beat phase. Put the rod at its
            // highest point at that instant: upright for bottom-pivot modes,
            // at a side extreme when the pivot is at the top.
            float swing = mode == PendulumMode.TOP
                    ? (float) Math.cos(Math.PI * phase)
                    : -(float) Math.sin(Math.PI * phase);
            lastAngle = maxAngle * swing;
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

        if (styleIndex == STYLE_QIANLI) {
            qianliArt.drawMoving(canvas, angle, bpm);
        } else {
            drawMovingParts(canvas, angle, phase, running);
            drawBpmPointer(canvas);
        }

        if (running || settling) {
            postInvalidateOnAnimation();
        }
    }

    private void computeGeometry(float w, float h) {
        bodyH = Math.min(h * 0.93f, w * 1.55f);
        if (styleIndex == STYLE_SONG) {
            // Leave headroom for the stopper knob and keep the SVG's 304:460
            // width-to-height ratio.
            bodyH *= 0.94f;
        }
        bodyW = bodyH * (styleIndex == STYLE_SONG ? 0.661f : 0.60f);
        cx = w / 2f;
        bodyBottom = h * (styleIndex == STYLE_SONG ? 0.94f : 0.965f);
        bodyTop = bodyBottom - bodyH;
        topHalf = bodyW * (styleIndex == STYLE_SONG ? 0.29f : 0.17f);
        botHalf = bodyW * 0.5f;
        rodW = Math.max(2.5f, bodyW * 0.014f);
        rodLen = bodyH * 0.70f;

        pivotY = mode == PendulumMode.BOTTOM
                ? bodyBottom - bodyH * 0.16f
                : bodyTop + bodyH * 0.10f;

        // Share the same scale geometry across styles so the marks and the
        // BPM pointer keep identical proportions (the Song gourd is wide
        // enough at every height to hold the full-length plate).
        plateTop = bodyTop + bodyH * 0.13f;
        plateBottom = bodyBottom - bodyH * 0.20f;
        plateTopHalf = styleIndex == STYLE_SONG ? bodyW * 0.085f : topHalf * 0.50f;
        plateBotHalf = styleIndex == STYLE_SONG ? bodyW * 0.16f : botHalf * 0.32f;

        // Taiji is the only element in this style: centre it in the card.
        taijiCy = h / 2f;
        taijiDiscR = Math.min(w, h) * 0.40f;
    }

    private float songSx() {
        return botHalf / 152f;
    }

    private float songSy() {
        return bodyH / 460f;
    }

    private float songX(float svgX, float sx) {
        return cx + (svgX - 200f) * sx;
    }

    private float songY(float svgY, float sy) {
        return bodyTop + (svgY - 68f) * sy;
    }

    private void drawSoftEllipse(Canvas c, float ex, float ey, float rx, float ry,
                                 float rotate, int centerColor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(0, 0, rx, centerColor, 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        c.save();
        c.translate(ex, ey);
        c.rotate(rotate);
        c.scale(1f, ry / rx);
        c.drawCircle(0, 0, rx, p);
        c.restore();
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

        if (styleIndex == STYLE_QIANLI) {
            qianliArt.drawStatic(c, w, h);
            cacheDirty = false;
            return;
        }

        drawBackdrop(c, w, h);

        // Soft shadow under the case (SVG ellipse cx=200 cy=545 rx=140 ry=12).
        if (styleIndex == STYLE_SONG) {
            float sx = songSx();
            float sy = songSy();
            paint.setColor(tone(0x295F8071));
            c.drawOval(cx - 140 * sx, bodyBottom + 5 * sy,
                    cx + 140 * sx, bodyBottom + 29 * sy, paint);
        } else {
            paint.setColor(tone(0x1F8A5A20));
            c.drawOval(cx - botHalf * 1.25f, bodyBottom - bodyH * 0.012f,
                    cx + botHalf * 1.25f, bodyBottom + bodyH * 0.035f, paint);
        }
        // The shadow's translucent alpha would otherwise mute the body shader.
        paint.setAlpha(0xFF);

        makeBodyPath(bodyPath);
        if (styleIndex == STYLE_SONG) {
            paint.setShader(new LinearGradient(cx, bodyTop, cx, bodyBottom,
                    new int[]{tone(0xFFFBFBF6), tone(0xFFEFF6F0),
                            tone(0xFFC8DBCE), tone(0xFFA9C6B6), tone(0xFF7F9F8E)},
                    null, Shader.TileMode.CLAMP));
        } else {
            paint.setShader(new LinearGradient(cx - botHalf, bodyTop, cx + botHalf, bodyBottom,
                    new int[]{tone(0xFFFFDC96), tone(0xFFF7BE6B),
                            tone(0xFFE39A45), tone(0xFFFFC976)},
                    null, Shader.TileMode.CLAMP));
        }
        c.drawPath(bodyPath, paint);
        paint.setShader(null);

        c.save();
        c.clipPath(bodyPath);
        drawBodyTexture(c);
        c.restore();

        drawBodyOutline(c);
        if (styleIndex == STYLE_SONG) {
            drawGourdDetails(c);
        }
        if (styleIndex != STYLE_SONG) {
            drawBase(c);
        }

        drawScalePlate(c);
        drawNamePlate(c);
        cacheDirty = false;
    }

    /** Backdrop differs per style: neon gets a synthwave grid, others a soft halo. */
    private void drawBackdrop(Canvas c, int w, int h) {
        if (styleIndex == STYLE_NEON) {
            paint.setColor(tone(0xFF0B1020));
            c.drawRect(0, 0, w, h, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(tone(0x8829E0E6));
            float horizon = h * 0.62f;
            for (int i = -8; i <= 8; i++) {
                c.drawLine(cx + i * w * 0.09f, horizon,
                        cx + i * w * 0.75f, h, paint);
            }
            for (int i = 1; i <= 10; i++) {
                float k = i / 10f;
                float y = horizon + (h - horizon) * k * k;
                c.drawLine(0, y, w, y, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else {
            paint.setShader(new RadialGradient(cx, h * 0.28f, Math.max(w, h) * 0.68f,
                    tone(0x33FFC94D), tone(0x00FFC94D), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    /** Case silhouette: trapezoid / vase / faceted / bottle / round-bottom. */
    private void makeBodyPath(Path path) {
        float top = bodyTop, bottom = bodyBottom;
        switch (styleIndex) {
            case STYLE_SONG:
                // Exact silhouette from the reference SVG (viewBox 30 15 340 550).
                {
                    float sx = songSx();
                    float sy = songSy();
                    path.reset();
                    path.moveTo(songX(200, sx), songY(68, sy));
                    path.cubicTo(songX(254, sx), songY(68, sy),
                            songX(288, sx), songY(104, sy),
                            songX(288, sx), songY(150, sy));
                    path.cubicTo(songX(288, sx), songY(188, sy),
                            songX(268, sx), songY(220, sy),
                            songX(240, sx), songY(242, sy));
                    path.cubicTo(songX(292, sx), songY(272, sy),
                            songX(352, sx), songY(308, sy),
                            songX(352, sx), songY(372, sy));
                    path.cubicTo(songX(352, sx), songY(458, sy),
                            songX(288, sx), songY(528, sy),
                            songX(200, sx), songY(528, sy));
                    path.cubicTo(songX(112, sx), songY(528, sy),
                            songX(48, sx), songY(458, sy),
                            songX(48, sx), songY(372, sy));
                    path.cubicTo(songX(48, sx), songY(308, sy),
                            songX(108, sx), songY(272, sy),
                            songX(160, sx), songY(242, sy));
                    path.cubicTo(songX(132, sx), songY(220, sy),
                            songX(112, sx), songY(188, sy),
                            songX(112, sx), songY(150, sy));
                    path.cubicTo(songX(112, sx), songY(104, sy),
                            songX(146, sx), songY(68, sy),
                            songX(200, sx), songY(68, sy));
                    path.close();
                }
                break;
            case STYLE_AURORA:
                // Round-bottom droplet, soft glass form.
                path.reset();
                path.moveTo(cx - topHalf, top);
                path.lineTo(cx + topHalf, top);
                path.cubicTo(cx + topHalf * 1.25f, top + bodyH * 0.34f,
                        cx + botHalf, bottom - bodyH * 0.32f,
                        cx + botHalf, bottom - bodyH * 0.12f);
                path.quadTo(cx + botHalf, bottom, cx + botHalf * 0.72f, bottom);
                path.lineTo(cx - botHalf * 0.72f, bottom);
                path.quadTo(cx - botHalf, bottom, cx - botHalf, bottom - bodyH * 0.12f);
                path.cubicTo(cx - botHalf, bottom - bodyH * 0.32f,
                        cx - topHalf * 1.25f, top + bodyH * 0.34f,
                        cx - topHalf, top);
                path.close();
                break;
            case STYLE_ROSE:
                // Rounded, full-bodied silhouette.
                path.reset();
                path.moveTo(cx - topHalf, top);
                path.lineTo(cx + topHalf, top);
                path.cubicTo(cx + topHalf * 1.55f, top + bodyH * 0.24f,
                        cx + botHalf * 0.96f, top + bodyH * 0.40f,
                        cx + botHalf * 0.96f, top + bodyH * 0.62f);
                path.quadTo(cx + botHalf * 0.96f, bottom, cx + botHalf * 0.80f, bottom);
                path.lineTo(cx - botHalf * 0.80f, bottom);
                path.quadTo(cx - botHalf * 0.96f, bottom, cx - botHalf * 0.96f, top + bodyH * 0.62f);
                path.cubicTo(cx - botHalf * 0.96f, top + bodyH * 0.40f,
                        cx - topHalf * 1.55f, top + bodyH * 0.24f,
                        cx - topHalf, top);
                path.close();
                break;
            case STYLE_NEON:
                // Faceted hexagonal case with clipped top corners.
                path.reset();
                path.moveTo(cx - topHalf * 0.55f, top);
                path.lineTo(cx + topHalf * 0.55f, top);
                path.lineTo(cx + topHalf, top + bodyH * 0.08f);
                path.lineTo(cx + botHalf, bottom - bodyH * 0.05f);
                path.lineTo(cx + botHalf * 0.86f, bottom);
                path.lineTo(cx - botHalf * 0.86f, bottom);
                path.lineTo(cx - botHalf, bottom - bodyH * 0.05f);
                path.lineTo(cx - topHalf, top + bodyH * 0.08f);
                path.close();
                break;
            default:
                makeTrapezoid(path, cx, top, topHalf, bottom, botHalf);
                break;
        }
    }

    /** Surface treatment unique to each style, clipped to the case. */
    private void drawBodyTexture(Canvas c) {
        paint.setStyle(Paint.Style.STROKE);
        switch (styleIndex) {
            case STYLE_SONG:
                // Decorations from the reference SVG, clipped to the body.
                {
                    float sx = songSx();
                    float sy = songSy();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(2f, 12 * sy));
                    paint.setColor(tone(0x734E6F60));
                    c.drawPath(bodyPath, paint);

                    paint.setStrokeWidth(Math.max(0.8f, 1.2f * sy));
                    paint.setColor(tone(0x2154796A));
                    int[] lines = {150, 178, 206, 300, 328, 356, 384, 412, 440, 468};
                    for (int ly : lines) {
                        float y = songY(ly, sy);
                        c.drawLine(songX(30, sx), y, songX(370, sx), y, paint);
                    }

                    drawSoftEllipse(c, songX(200, sx), songY(528, sy),
                            128 * sx, 48 * sy, 0f, tone(0x473F5A4E));
                    drawSoftEllipse(c, songX(150, sx), songY(118, sy),
                            32 * sx, 46 * sy, -16f, tone(0x80FFFFFF));
                    drawSoftEllipse(c, songX(176, sx), songY(102, sy),
                            12 * sx, 20 * sy, -18f, tone(0xD9FFFFFF));
                    drawSoftEllipse(c, songX(114, sx), songY(316, sy),
                            26 * sx, 58 * sy, -12f, tone(0x47FFFFFF));

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(2f, 6 * sy));
                    paint.setColor(tone(0xFFAD4F3D));
                    Path cord = new Path();
                    cord.moveTo(songX(148, sx), songY(244, sy));
                    cord.quadTo(songX(200, sx), songY(268, sy),
                            songX(252, sx), songY(244, sy));
                    c.drawPath(cord, paint);
                    paint.setStrokeWidth(Math.max(1f, 3 * sy));
                    paint.setColor(tone(0xCC8C3E30));
                    Path cord2 = new Path();
                    cord2.moveTo(songX(140, sx), songY(254, sy));
                    cord2.quadTo(songX(200, sx), songY(280, sy),
                            songX(260, sx), songY(254, sy));
                    c.drawPath(cord2, paint);
                }
                break;
            case STYLE_AURORA:
                // Glass sheen: two broad translucent vertical highlights.
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new LinearGradient(cx - botHalf, 0, cx + botHalf * 0.3f, 0,
                        new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF},
                        null, Shader.TileMode.CLAMP));
                c.drawRect(cx - botHalf, bodyTop, cx + botHalf, bodyBottom, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, bodyW * 0.02f));
                paint.setColor(0x55FFFFFF);
                c.drawLine(cx + botHalf * 0.45f, bodyTop + bodyH * 0.08f,
                        cx + botHalf * 0.30f, bodyBottom - bodyH * 0.10f, paint);
                break;
            case STYLE_ROSE:
                // Brushed-metal vertical striations.
                paint.setStrokeWidth(Math.max(0.8f, bodyH * 0.0011f));
                for (int i = 0; i < 52; i++) {
                    float x = cx - botHalf + (2 * botHalf) * i / 52f;
                    paint.setColor(i % 2 == 0 ? 0x2AFFFFFF : 0x1E000000);
                    c.drawLine(x, bodyTop, x, bodyBottom, paint);
                }
                break;
            case STYLE_NEON:
                // Circuit grid.
                paint.setStrokeWidth(1f);
                paint.setColor(tone(0x6637F0E0));
                for (int i = 1; i < 18; i++) {
                    float y = bodyTop + bodyH * i / 18f;
                    c.drawLine(cx - botHalf, y, cx + botHalf, y, paint);
                }
                for (int i = 1; i < 12; i++) {
                    float x = cx - botHalf + (2 * botHalf) * i / 12f;
                    c.drawLine(x, bodyTop, x, bodyBottom, paint);
                }
                paint.setColor(tone(0xCC29E0E6));
                c.drawLine(cx, bodyTop, cx, bodyBottom, paint);
                break;
            default:
                // Warm wood grain.
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
                    paint.setColor(i % 3 == 0 ? tone(0x44FFFFFF) : tone(0x18B9681A));
                    c.drawPath(grain, paint);
                }
                break;
        }
    }

    private void drawBodyOutline(Canvas c) {
        if (styleIndex == STYLE_NEON || styleIndex == STYLE_AURORA) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(5f, bodyH * 0.011f));
            paint.setColor(styleIndex == STYLE_NEON ? tone(0x5529E0E6) : tone(0x4480D0FF));
            c.drawPath(bodyPath, paint);
        }
        if (styleIndex == STYLE_SONG) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, 4 * songSy()));
            paint.setColor(tone(0xFF557A6B));
            c.drawPath(bodyPath, paint);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, bodyH * 0.004f));
        paint.setColor(styleIndex == STYLE_NEON ? tone(0xFF29E0E6)
                : styleIndex == STYLE_AURORA ? tone(0xAAFFFFFF)
                : styleIndex == STYLE_ROSE ? tone(0xFFFFE0C0)
                : tone(0xFFF0A93B));
        c.drawPath(bodyPath, paint);
        if (styleIndex == STYLE_SUNNY) {
            paint.setStrokeWidth(1f);
            paint.setColor(tone(0xAAFFFFFF));
            c.drawLine(cx - topHalf, bodyTop, cx - botHalf * 0.97f, bodyBottom, paint);
        }
    }

    /** Silver stopper from the reference SVG. */
    private void drawGourdDetails(Canvas c) {
        float sx = songSx();
        float sy = songSy();
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        // Knob: circle cx=200 cy=36 r=7.
        float kx = songX(200, sx);
        float ky = songY(36, sy);
        float kr = 7 * sx;
        paint.setShader(new RadialGradient(kx - kr * 0.35f, ky - kr * 0.4f,
                kr * 1.5f, tone(0xFFF7F8F5), tone(0xFF9AA8A1),
                Shader.TileMode.CLAMP));
        c.drawCircle(kx, ky, kr, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 2 * sy));
        paint.setColor(tone(0xFF6D736F));
        c.drawCircle(kx, ky, kr, paint);

        // Cap: rect x=172 y=44 w=56 h=26 rx=8.
        RectF cap = new RectF(songX(172, sx), songY(44, sy),
                songX(228, sx), songY(70, sy));
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, cap.top, 0, cap.bottom,
                new int[]{tone(0xFFF7F8F5), tone(0xFFC9D1CB),
                        tone(0xFF9AA8A1), tone(0xFF7E8983)},
                new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(cap, 8 * sx, 8 * sx, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 2.5f * sy));
        paint.setColor(tone(0xFF6D736F));
        c.drawRoundRect(cap, 8 * sx, 8 * sx, paint);

        // Base ring: rect x=166 y=66 w=68 h=12 rx=6.
        RectF ring = new RectF(songX(166, sx), songY(66, sy),
                songX(234, sx), songY(78, sy));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(0xFF9AA39D));
        c.drawRoundRect(ring, 6 * sx, 6 * sx, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 2 * sy));
        paint.setColor(tone(0xFF6D736F));
        c.drawRoundRect(ring, 6 * sx, 6 * sx, paint);
    }

    private void drawBase(Canvas c) {
        float baseH = bodyH * 0.045f;
        RectF base = new RectF(cx - botHalf * 1.06f, bodyBottom - baseH,
                cx + botHalf * 1.06f, bodyBottom + baseH * 0.15f);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, base.top, 0, base.bottom,
                tone(0xFFF0A844), tone(0xFFD87A24), Shader.TileMode.CLAMP));
        c.drawRoundRect(base, baseH * 0.35f, baseH * 0.35f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(tone(0xFFFFD77A));
        c.drawRoundRect(base, baseH * 0.35f, baseH * 0.35f, paint);
    }

    private void drawScalePlate(Canvas c) {
        makeTrapezoid(platePath, cx, plateTop, plateTopHalf, plateBottom, plateBotHalf);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(cx - plateBotHalf, 0, cx + plateBotHalf, 0,
                new int[]{tone(0xFFEAF6FF), tone(0xFFFFFFFF), tone(0xFFFFFBF0), tone(0xFFE4F2FA)},
                null, Shader.TileMode.CLAMP));
        c.drawPath(platePath, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, bodyH * 0.003f));
        paint.setColor(tone(0xFF8FD3E8));
        c.drawPath(platePath, paint);
        if (styleIndex == STYLE_SONG) {
            // A fine ink hairline inside the celadon rim keeps the rice-paper
            // scale crisp without making it visually heavier.
            float inset = bodyW * 0.012f;
            Path innerPlate = new Path();
            makeTrapezoid(innerPlate, cx,
                    plateTop + inset * 0.6f, plateTopHalf - inset,
                    plateBottom - inset * 0.6f, plateBotHalf - inset);
            paint.setStrokeWidth(Math.max(0.8f, bodyH * 0.0012f));
            paint.setColor(tone(0x55556F63));
            c.drawPath(innerPlate, paint);
        }

        // Recess in which the rod travels.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(0x123A8FB0));
        c.drawRect(cx - rodW * 1.4f, plateTop, cx + rodW * 1.4f, plateBottom, paint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(Math.max(9f, bodyH * 0.021f));
        textPaint.setColor(tone(0xFF3D6B8A));
        paint.setStrokeWidth(Math.max(1f, bodyH * 0.0018f));

        for (int mark : MARKS) {
            float t = markPosition(mark);
            float y = plateTop + (plateBottom - plateTop) * t;
            float half = plateTopHalf + (plateBotHalf - plateTopHalf) * t;
            paint.setColor(tone(0xFF7FBBD4));
            c.drawLine(cx + half * 0.18f, y, cx + half * 0.82f, y, paint);
            c.drawText(String.valueOf(mark), cx - half * 0.16f,
                    y + textPaint.getTextSize() * 0.36f, textPaint);
        }
    }

    private void drawNamePlate(Canvas c) {
        if (styleIndex == STYLE_SONG) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(bodyH * 0.028f);
            textPaint.setColor(tone(0xFF3E544B));
            c.drawText(STYLE_LABELS[styleIndex], cx,
                    bodyBottom - bodyH * 0.055f, textPaint);
            return;
        }
        float plateW = Math.min(botHalf * 1.05f, bodyW * 0.5f);
        float plateH = bodyH * 0.05f;
        float y = bodyBottom - bodyH * 0.085f;
        RectF rect = new RectF(cx - plateW / 2, y - plateH / 2,
                cx + plateW / 2, y + plateH / 2);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                new int[]{tone(0xFFFFE79C), tone(0xFFFFB44D), tone(0xFFFF8A3D)},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, plateH * 0.25f, plateH * 0.25f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(tone(0xFFE4761F));
        c.drawRoundRect(rect, plateH * 0.25f, plateH * 0.25f, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(plateH * 0.48f);
        textPaint.setColor(tone(0xFF7A3B0A));
        c.drawText(STYLE_LABELS[styleIndex], cx, y + plateH * 0.20f, textPaint);
    }

    /**
     * The yin-yang disc turns half a revolution on every beat. The engine's
     * continuous beat phase keeps the rotation locked to the audible click;
     * wrapping at two beats returns to the identical 360-degree orientation,
     * so there is no visible jump at the loop point.
     */
    private void drawTaijiMoving(Canvas c, double phase, boolean running) {
        if (running) {
            float step = (float) (180.0 * (phase % 2.0));
            taijiAngle = taijiClockwise ? step : -step;
        } else if (wasRunning) {
            taijiAngle = Math.round(taijiAngle / 180f) * 180f;
        }
        c.save();
        c.rotate(taijiAngle, cx, taijiCy);
        drawTaijiDisc(c);
        c.restore();
    }

    private void drawTaijiDisc(Canvas c) {
        float r = taijiDiscR;
        float smallR = r / 2f;
        float dotR = r / 8f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(TAIJI_RICE);
        c.drawCircle(cx, taijiCy, r, paint);

        paint.setColor(TAIJI_INK);
        c.drawArc(new RectF(cx - r, taijiCy - r, cx + r, taijiCy + r),
                90f, 180f, true, paint);

        c.drawCircle(cx, taijiCy - smallR, smallR, paint);
        paint.setColor(TAIJI_RICE);
        c.drawCircle(cx, taijiCy + smallR, smallR, paint);

        paint.setColor(TAIJI_RICE);
        c.drawCircle(cx, taijiCy - smallR, dotR, paint);
        paint.setColor(TAIJI_INK);
        c.drawCircle(cx, taijiCy + smallR, dotR, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, r * 0.006f));
        paint.setColor(TAIJI_INK);
        c.drawCircle(cx, taijiCy, r, paint);
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
        float top = Math.min(pivotY, yEnd);
        float bottom = Math.max(pivotY, yEnd);
        if (styleIndex == STYLE_NEON || styleIndex == STYLE_AURORA) {
            // Glowing light-rod: wide soft halo + bright core.
            paint.setColor(styleIndex == STYLE_NEON ? tone(0x5529E0E6) : tone(0x5580D0FF));
            c.drawRect(left - rodW * 1.6f, top, right + rodW * 1.6f, bottom, paint);
            paint.setColor(tone(0xFFFFFFFF));
            c.drawRect(left, top, right, bottom, paint);
        } else {
            paint.setShader(new LinearGradient(left, 0, right, 0,
                    new int[]{tone(0xFFF0A22E), tone(0xFFFFF4C2),
                            tone(0xFFFFC23D), tone(0xFFE07C1C)},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(left, top, right, bottom, paint);
            paint.setShader(null);
        }

        // Sliding weight: faster tempi put the weight closer to the pivot,
        // exactly like a real pendulum metronome.
        float t = (bpm - MetronomeEngine.MIN_BPM)
                / (float) (MetronomeEngine.MAX_BPM - MetronomeEngine.MIN_BPM);
        float weightAt = 0.90f - 0.44f * t;
        float wy = pivotY + dir * rodLen * weightAt;
        drawWeight(c, wy);

        // Tip of the rod.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(0xFFFFC94D));
        c.drawCircle(cx, yEnd, rodW * 1.5f, paint);
    }

    /** Weight shape is style-specific: brass block / gem / glowing orb. */
    private void drawWeight(Canvas c, float wy) {
        float halfW = bodyW * 0.055f;
        float halfH = bodyH * 0.023f;
        paint.setStyle(Paint.Style.FILL);
        if (styleIndex == STYLE_NEON || styleIndex == STYLE_AURORA) {
            float r = halfW * 1.15f;
            paint.setColor(styleIndex == STYLE_NEON ? tone(0x6629E0E6) : tone(0x6680D0FF));
            c.drawCircle(cx, wy, r * 1.8f, paint);
            paint.setShader(new RadialGradient(cx - r * 0.35f, wy - r * 0.4f, r * 1.5f,
                    tone(0xFFFFFFFF), tone(styleIndex == STYLE_NEON ? 0xFF29E0E6 : 0xFF2E9CB6),
                    Shader.TileMode.CLAMP));
            c.drawCircle(cx, wy, r, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(tone(0xCCFFFFFF));
            c.drawCircle(cx, wy, r, paint);
            return;
        }
        if (styleIndex == STYLE_ROSE) {
            // Faceted diamond gem.
            Path gem = new Path();
            gem.moveTo(cx, wy - halfH * 1.5f);
            gem.lineTo(cx + halfW, wy);
            gem.lineTo(cx, wy + halfH * 1.5f);
            gem.lineTo(cx - halfW, wy);
            gem.close();
            paint.setShader(new LinearGradient(cx - halfW, 0, cx + halfW, 0,
                    new int[]{tone(0xFFE58B26), tone(0xFFFFF0B0),
                            tone(0xFFFFB22E), tone(0xFFD46E14)},
                    null, Shader.TileMode.CLAMP));
            c.drawPath(gem, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(tone(0xFFB45A12));
            c.drawPath(gem, paint);
            paint.setColor(tone(0xCCFFFFFF));
            c.drawLine(cx - halfW * 0.5f, wy, cx + halfW * 0.5f, wy, paint);
            return;
        }
        if (styleIndex == STYLE_SONG) {
            // Celadon bar weight with gilt end caps.
            paint.setShader(new LinearGradient(cx - halfW, 0, cx + halfW, 0,
                    new int[]{tone(0xFF3E544B), tone(0xFFE6F0E9),
                            tone(0xFFA8C8B8), tone(0xFF3E544B)},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(cx - halfW, wy - halfH, cx + halfW, wy + halfH,
                    halfH * 0.6f, halfH * 0.6f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tone(0xFFC6A15B));
            float capW = halfW * 0.28f;
            c.drawRoundRect(cx - halfW, wy - halfH, cx - halfW + capW, wy + halfH,
                    halfH * 0.6f, halfH * 0.6f, paint);
            c.drawRoundRect(cx + halfW - capW, wy - halfH, cx + halfW, wy + halfH,
                    halfH * 0.6f, halfH * 0.6f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(tone(0xFF3E544B));
            c.drawRoundRect(cx - halfW, wy - halfH, cx + halfW, wy + halfH,
                    halfH * 0.6f, halfH * 0.6f, paint);
            return;
        }
        // Default: warm brass trapezoid.
        Path weight = new Path();
        weight.moveTo(cx - halfW * 0.72f, wy - halfH);
        weight.lineTo(cx + halfW * 0.72f, wy - halfH);
        weight.lineTo(cx + halfW, wy + halfH);
        weight.lineTo(cx - halfW, wy + halfH);
        weight.close();
        paint.setShader(new LinearGradient(cx - halfW, 0, cx + halfW, 0,
                new int[]{tone(0xFFE58B26), tone(0xFFFFF0B0),
                        tone(0xFFFFB22E), tone(0xFFD46E14)},
                null, Shader.TileMode.CLAMP));
        c.drawPath(weight, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(tone(0xFFB45A12));
        c.drawPath(weight, paint);
        paint.setColor(tone(0xCCFFFFFF));
        c.drawLine(cx - halfW * 0.6f, wy, cx + halfW * 0.6f, wy, paint);
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
        paint.setColor(tone(0xFF2EC4B6));
        c.drawPath(spring, paint);
        paint.setStrokeWidth(Math.max(1f, bodyH * 0.003f));
        paint.setColor(tone(0xCCEFFFFB));
        c.drawPath(spring, paint);

        float bobR = bodyW * 0.072f;
        float by = pivotY + length + bobR;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx - bobR * 0.35f, by - bobR * 0.4f,
                bobR * 1.5f, tone(0xFFFFF3B0), tone(0xFFFF8A3D), Shader.TileMode.CLAMP));
        c.drawCircle(cx, by, bobR, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(tone(0xFFE06A1A));
        c.drawCircle(cx, by, bobR, paint);
    }

    private void drawPivot(Canvas c) {
        float r = bodyW * 0.038f;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx - r * 0.35f, pivotY - r * 0.4f, r * 1.6f,
                tone(0xFFFFFFFF), tone(0xFF2EC4B6), Shader.TileMode.CLAMP));
        c.drawCircle(cx, pivotY, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(tone(0xFF159C90));
        c.drawCircle(cx, pivotY, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone(0x88159C90));
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
        paint.setColor(tone(0xFFFF5A5F));
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
