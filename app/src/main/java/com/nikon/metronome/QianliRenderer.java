package com.nikon.metronome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * OpenGL ES 2.0 renderer for the "A Thousand Li of Rivers and Mountains"
 * metronome. The body is a four-sided pyramid whose four triangular faces and
 * square base are textured with the supplied artwork (front/back/left/right/
 * down). A brass rod and sliding weight swing along the front face in sync
 * with the engine. Touch-drag orbits the model.
 */
final class QianliRenderer implements GLSurfaceView.Renderer {

    private static final int STRIDE_FLOATS = 12; // pos3 + normal3 + color4 + uv2

    private static final float[] IVORY = col(0xFFF6F0E1);
    private static final float[] GOLD = col(0xFFD4A94E);
    private static final float[] GOLD_LT = col(0xFFFFE6A6);
    private static final float[] GOLD_DK = col(0xFF9A6E1E);
    private static final float[] NORM_FRONT = norm(0f, 0.12f, 1f);

    // Square-based pyramid with a small FLAT top (no sharp apex) and gently
    // rolled vertical arrises. Base is square so down.png maps evenly.
    private static final float HALF = 0.70f;
    private static final float Y0 = -0.75f, Y1 = 0.78f;
    private static final float TOP_SCALE = 0.22f;  // top half-size vs base (flat cap)
    private static final int RING = 64;            // samples around each horizontal ring
    private static final int PER_FACE = RING / 4;
    private static final int LEVELS = 8;           // vertical rings (face stays planar)
    private static final float CORNER_R = 0.16f;   // rounded-arrix radius fraction (small)
    // UV of the triangle drawn inside each 1312x1312 face image.
    private static final float UV_TOP_V = 0.047f;
    private static final float UV_BOT_V = 0.926f;
    private static final float UV_L = 0.046f;
    private static final float UV_R = 0.954f;
    // front.png scale slot sits ~8px right of image centre; shift texture left.
    private static final float FRONT_UV_SHIFT = 0.0065f;
    // down.png gilt frame measured from the artwork (x 0.155..0.839, y 0.163..0.839).
    private static final float DOWN_U0 = 0.157f, DOWN_U1 = 0.837f;
    private static final float DOWN_V0 = 0.165f, DOWN_V1 = 0.837f;

    private static final String VS =
            "attribute vec3 aPosition;" +
            "attribute vec3 aNormal;" +
            "attribute vec4 aColor;" +
            "attribute vec2 aUV;" +
            "uniform mat4 uMVP;" +
            "uniform mat4 uModel;" +
            "varying vec3 vNormal;" +
            "varying vec4 vColor;" +
            "varying vec2 vUV;" +
            "void main(){" +
            "  vNormal = mat3(uModel) * aNormal;" +
            "  vColor = aColor;" +
            "  vUV = aUV;" +
            "  gl_Position = uMVP * vec4(aPosition, 1.0);" +
            "}";

    private static final String FS =
            "precision mediump float;" +
            "varying vec3 vNormal;" +
            "varying vec4 vColor;" +
            "varying vec2 vUV;" +
            "uniform float uLightMix;" +
            "uniform float uUseTex;" +
            "uniform float uAlpha;" +
            "uniform float uGloss;" +
            "uniform sampler2D uTexture;" +
            "void main(){" +
            "  vec3 n = normalize(vNormal);" +
            "  vec3 keyDir = normalize(vec3(-0.45, 0.85, 0.75));" +
            "  vec3 fillDir = normalize(vec3(0.80, 0.25, -0.35));" +
            "  float key = max(dot(n, keyDir), 0.0);" +
            "  float fill = max(dot(n, fillDir), 0.0);" +
            "  float diffuse = 0.46 + 0.42 * key + 0.18 * fill;" +
            "  float ao = mix(1.0, 0.86 + 0.14 * (1.0 - vUV.y), uUseTex);" +
            "  vec4 texel = texture2D(uTexture, vUV);" +
            "  vec3 base = mix(vColor.rgb, texel.rgb, uUseTex);" +
            "  vec3 lit = base * mix(1.0, diffuse * ao, uLightMix);" +
            "  vec3 viewDir = vec3(0.0, 0.0, 1.0);" +
            "  vec3 halfDir = normalize(keyDir + viewDir);" +
            "  float spec = pow(max(dot(n, halfDir), 0.0), 26.0) * uGloss;" +
            "  lit += vec3(1.0, 0.86, 0.56) * spec;" +
            "  float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0) * uGloss;" +
            "  lit += vec3(0.16, 0.25, 0.32) * rim * 0.35;" +
            "  float alpha = vColor.a * mix(1.0, texel.a, uUseTex) * uAlpha;" +
            "  gl_FragColor = vec4(lit, alpha);" +
            "}";

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] projView = new float[16];
    private final float[] model = new float[16];
    private final float[] rodModel = new float[16];
    private final float[] weightModel = new float[16];
    private final float[] tmp = new float[16];
    private final float[] mvp = new float[16];
    private final float[] identity = new float[16];

    private int program;
    private int aPos, aNormal, aColor, aUV;
    private int uMVP, uModel, uLightMix, uUseTex, uAlpha, uGloss, uTexture;
    private int viewportW = 1, viewportH = 1;

    // One texture and one mesh per face, plus a generated soft shadow texture.
    private static final int TEX_FRONT = 0, TEX_BACK = 1, TEX_LEFT = 2,
            TEX_RIGHT = 3, TEX_DOWN = 4, TEX_SHADOW = 5, TEX_COUNT = 6;
    private final int[] textures = new int[TEX_COUNT];
    private GLMesh frontMesh, backMesh, leftMesh, rightMesh, downMesh, topMesh;
    private GLMesh rodMesh, weightMesh, haloMesh, contactShadowMesh;

    private Resources resources;
    private MetronomeEngine engine;
    private int bpm = 120;
    private boolean runningPrev;
    private boolean settling;
    private float lastAngle, settleStartAngle;
    private long settleStartNanos;

    private float yaw = 18f;
    private float pitch = 7f;
    private float lastTouchX, lastTouchY;
    private boolean touching;

    void setResources(Resources resources) {
        this.resources = resources;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
        GLES20.glClearColor(0.965f, 0.953f, 0.902f, 1f);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        Matrix.setIdentityM(identity, 0);
        program = buildProgram(VS, FS);
        aPos = GLES20.glGetAttribLocation(program, "aPosition");
        aNormal = GLES20.glGetAttribLocation(program, "aNormal");
        aColor = GLES20.glGetAttribLocation(program, "aColor");
        aUV = GLES20.glGetAttribLocation(program, "aUV");
        uMVP = GLES20.glGetUniformLocation(program, "uMVP");
        uModel = GLES20.glGetUniformLocation(program, "uModel");
        uLightMix = GLES20.glGetUniformLocation(program, "uLightMix");
        uUseTex = GLES20.glGetUniformLocation(program, "uUseTex");
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha");
        uGloss = GLES20.glGetUniformLocation(program, "uGloss");
        uTexture = GLES20.glGetUniformLocation(program, "uTexture");
        loadTextures();
        buildMeshes();
    }

    private void loadTextures() {
        int[] drawables = {R.drawable.front, R.drawable.back, R.drawable.left,
                R.drawable.right, R.drawable.down};
        int[] generated = new int[TEX_COUNT];
        GLES20.glGenTextures(TEX_COUNT, generated, 0);
        for (int i = 0; i < drawables.length; i++) {
            textures[i] = generated[i];
            Bitmap bmp = BitmapFactory.decodeResource(resources, drawables[i]);
            bindTexture(textures[i]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            bmp.recycle();
        }

        textures[TEX_SHADOW] = generated[TEX_SHADOW];
        bindTexture(textures[TEX_SHADOW]);
        Bitmap shadow = createShadowBitmap();
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, shadow, 0);
        shadow.recycle();
    }

    private static void bindTexture(int texId) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private static Bitmap createShadowBitmap() {
        int size = 128;
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            float dy = (y + 0.5f) / size * 2f - 1f;
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f) / size * 2f - 1f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = Math.max(0f, 1f - d);
                a = a * a * (3f - 2f * a);
                int alpha = (int) (a * 210f);
                pixels[y * size + x] = alpha << 24;
            }
        }
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, size, 0, 0, size, size);
        return bmp;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        viewportW = Math.max(1, w);
        viewportH = Math.max(1, h);
        GLES20.glViewport(0, 0, viewportW, viewportH);
        float aspect = (float) viewportW / viewportH;
        Matrix.perspectiveM(projection, 0, 45f, aspect, 0.1f, 100f);
        float dist = aspect > 1.5f ? 4.6f : 3.6f;
        Matrix.setLookAtM(view, 0, 0f, 0.55f, dist, 0f, 0f, 0f, 0f, 1f, 0f);
        Matrix.multiplyMM(projView, 0, projection, 0, view, 0);
        requestRender();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);

        boolean running = engine != null && engine.isRunning();
        double phase = running ? engine.getBeatPhase() : 0.0;
        float angle = swingAngle(running, phase);

        Matrix.setIdentityM(model, 0);
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f);
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f);

        // Rod swings in the front plane, pinned near the dial on the front face.
        float pivotY = -0.18f;
        float pz = zFront(pivotY) + 0.05f;
        Matrix.setIdentityM(tmp, 0);
        Matrix.translateM(tmp, 0, 0f, pivotY, pz);
        Matrix.rotateM(tmp, 0, angle, 0f, 0f, 1f);
        Matrix.multiplyMM(rodModel, 0, model, 0, tmp, 0);

        // Weight slides along the rod with tempo.
        float t = (bpm - 40f) / 200f;
        float wy = 0.82f - 0.38f * t;
        Matrix.setIdentityM(tmp, 0);
        Matrix.translateM(tmp, 0, 0f, pivotY, pz);
        Matrix.rotateM(tmp, 0, angle, 0f, 0f, 1f);
        Matrix.translateM(tmp, 0, 0f, wy, 0f);
        Matrix.multiplyMM(weightModel, 0, model, 0, tmp, 0);

        // Soft contact and ambient shadows behind the body.
        GLES20.glDepthMask(false);
        drawShadow(haloMesh, 0.52f);
        drawShadow(contactShadowMesh, 0.95f);
        GLES20.glDepthMask(true);

        // Textured faces.
        draw(frontMesh, model, 0.55f, textures[TEX_FRONT]);
        draw(backMesh, model, 0.48f, textures[TEX_BACK]);
        draw(leftMesh, model, 0.42f, textures[TEX_LEFT]);
        draw(rightMesh, model, 0.62f, textures[TEX_RIGHT]);
        draw(downMesh, model, 0.35f, textures[TEX_DOWN]);
        drawPlain(topMesh, model, 0.55f, 0.12f);

        // Moving brass parts (flat colour, no texture).
        drawPlain(rodMesh, rodModel, 1f, 1f);
        drawPlain(weightMesh, weightModel, 0.85f, 1f);

        if (running || settling) {
            requestRender();
        }
    }

    private void draw(GLMesh mesh, float[] modelMat, float lightMix, int texId) {
        if (mesh == null || mesh.count == 0) return;
        Matrix.multiplyMM(mvp, 0, projView, 0, modelMat, 0);
        if (uMVP >= 0) GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        if (uModel >= 0) GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0);
        if (uLightMix >= 0) GLES20.glUniform1f(uLightMix, lightMix);
        if (uUseTex >= 0) GLES20.glUniform1f(uUseTex, 1f);
        if (uAlpha >= 0) GLES20.glUniform1f(uAlpha, 1f);
        if (uGloss >= 0) GLES20.glUniform1f(uGloss, 0f);
        if (uTexture >= 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
            GLES20.glUniform1i(uTexture, 0);
        }
        bindAttributes(mesh);
        GLES20.glDrawArrays(mesh.mode, 0, mesh.count);
    }

    private void drawPlain(GLMesh mesh, float[] modelMat, float lightMix, float gloss) {
        if (mesh == null || mesh.count == 0) return;
        Matrix.multiplyMM(mvp, 0, projView, 0, modelMat, 0);
        if (uMVP >= 0) GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        if (uModel >= 0) GLES20.glUniformMatrix4fv(uModel, 1, false, modelMat, 0);
        if (uLightMix >= 0) GLES20.glUniform1f(uLightMix, lightMix);
        if (uUseTex >= 0) GLES20.glUniform1f(uUseTex, 0f);
        if (uAlpha >= 0) GLES20.glUniform1f(uAlpha, 1f);
        if (uGloss >= 0) GLES20.glUniform1f(uGloss, gloss);
        bindAttributes(mesh);
        GLES20.glDrawArrays(mesh.mode, 0, mesh.count);
    }

    private void drawShadow(GLMesh mesh, float alpha) {
        if (mesh == null || mesh.count == 0) return;
        Matrix.multiplyMM(mvp, 0, projView, 0, identity, 0);
        if (uMVP >= 0) GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        if (uModel >= 0) GLES20.glUniformMatrix4fv(uModel, 1, false, identity, 0);
        if (uLightMix >= 0) GLES20.glUniform1f(uLightMix, 0f);
        if (uUseTex >= 0) GLES20.glUniform1f(uUseTex, 1f);
        if (uAlpha >= 0) GLES20.glUniform1f(uAlpha, alpha);
        if (uGloss >= 0) GLES20.glUniform1f(uGloss, 0f);
        if (uTexture >= 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[TEX_SHADOW]);
            GLES20.glUniform1i(uTexture, 0);
        }
        bindAttributes(mesh);
        GLES20.glDrawArrays(mesh.mode, 0, mesh.count);
    }

    private void bindAttributes(GLMesh mesh) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vbo);
        if (aPos >= 0) {
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE_FLOATS * 4, 0);
        }
        if (aNormal >= 0) {
            GLES20.glEnableVertexAttribArray(aNormal);
            GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, STRIDE_FLOATS * 4, 12);
        }
        if (aColor >= 0) {
            GLES20.glEnableVertexAttribArray(aColor);
            GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, STRIDE_FLOATS * 4, 24);
        }
        if (aUV >= 0) {
            GLES20.glEnableVertexAttribArray(aUV);
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, STRIDE_FLOATS * 4, 40);
        }
    }

    private float swingAngle(boolean running, double phase) {
        float maxAngle = 20f;
        if (running) {
            settling = false;
            lastAngle = -maxAngle * (float) Math.sin(Math.PI * phase);
            runningPrev = true;
            return lastAngle;
        }
        if (runningPrev) {
            settling = true;
            settleStartAngle = lastAngle;
            settleStartNanos = System.nanoTime();
            runningPrev = false;
        }
        if (settling) {
            float t = (System.nanoTime() - settleStartNanos) / 900_000_000f;
            if (t >= 1f) {
                settling = false;
                return 0f;
            }
            return settleStartAngle * (float) Math.exp(-3.2 * t)
                    * (float) Math.cos(11.0 * t);
        }
        return 0f;
    }

    // --------------------------------------------------------------
    // Public controls
    // --------------------------------------------------------------

    void attach(MetronomeEngine engine) {
        this.engine = engine;
    }

    void setBpm(int bpm) {
        this.bpm = bpm;
        requestRender();
    }

    void onEngineStateChanged() {
        requestRender();
    }

    boolean onTouch(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lastTouchX = e.getX();
            lastTouchY = e.getY();
            touching = true;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && touching) {
            float dx = e.getX() - lastTouchX;
            float dy = e.getY() - lastTouchY;
            yaw += dx * 0.45f;
            pitch += dy * 0.35f;
            if (pitch < -40f) pitch = -40f;
            if (pitch > 40f) pitch = 40f;
            lastTouchX = e.getX();
            lastTouchY = e.getY();
            requestRender();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touching = false;
            return true;
        }
        return false;
    }

    private void requestRender() {
        if (host != null) host.requestRender();
    }

    private GLSurfaceView host;

    void setHost(GLSurfaceView host) {
        this.host = host;
    }

    // --------------------------------------------------------------
    // Geometry
    // --------------------------------------------------------------

    private void buildMeshes() {
        float[] bfl = pt(-HALF, Y0, HALF), bfr = pt(HALF, Y0, HALF);
        float[] bbr = pt(HALF, Y0, -HALF), bbl = pt(-HALF, Y0, -HALF);

        // Four side faces as rounded parametric surfaces. Each face spans one
        // quarter of a superellipse ring; rings shrink toward a rounded apex.
        frontMesh = roundedFace(0);   // +Z
        rightMesh = roundedFace(1);   // +X
        backMesh = roundedFace(2);    // -Z
        leftMesh = roundedFace(3);    // -X

        // Square base cropped to the gilt frame of down.png.
        MeshBuilder base = new MeshBuilder();
        float[] dn = norm(0f, -1f, 0f);
        base.v(bfl, dn, IVORY, DOWN_U0, DOWN_V1);
        base.v(bbl, dn, IVORY, DOWN_U1, DOWN_V1);
        base.v(bbr, dn, IVORY, DOWN_U1, DOWN_V0);
        base.v(bfl, dn, IVORY, DOWN_U0, DOWN_V1);
        base.v(bbr, dn, IVORY, DOWN_U1, DOWN_V0);
        base.v(bfr, dn, IVORY, DOWN_U0, DOWN_V0);
        downMesh = base.build(GLES20.GL_TRIANGLES);

        // Small flat top cap so the apex is smooth, not a sharp point.
        float th = HALF * TOP_SCALE;
        float[] tl = pt(-th, Y1, th), trr = pt(th, Y1, th);
        float[] tbr = pt(th, Y1, -th), tbl = pt(-th, Y1, -th);
        MeshBuilder cap = new MeshBuilder();
        float[] up = norm(0f, 1f, 0f);
        cap.tri(tl, trr, tbr, up, IVORY, IVORY, IVORY);
        cap.tri(tl, tbr, tbl, up, IVORY, IVORY, IVORY);
        topMesh = cap.build(GLES20.GL_TRIANGLES);

        // Brass rod: a slim box from local y=0 up to y=len.
        MeshBuilder rod = new MeshBuilder();
        float w = 0.012f, len = 0.92f;
        float[] p000 = pt(-w, 0, -w), p100 = pt(w, 0, -w), p110 = pt(w, 0, w), p010 = pt(-w, 0, w);
        float[] q000 = pt(-w, len, -w), q100 = pt(w, len, -w), q110 = pt(w, len, w), q010 = pt(-w, len, w);
        rod.quad(p010, p110, q110, q010, norm(0f, 0f, 1f), GOLD, GOLD, GOLD_LT, GOLD_LT);
        rod.quad(p100, p000, q000, q100, norm(0f, 0f, -1f), GOLD_DK, GOLD_DK, GOLD, GOLD);
        rod.quad(p000, p010, q010, q000, norm(-1f, 0f, 0f), GOLD_DK, GOLD, GOLD, GOLD_DK);
        rod.quad(p110, p100, q100, q110, norm(1f, 0f, 0f), GOLD, GOLD_DK, GOLD_DK, GOLD);
        rod.quad(q010, q110, q100, q000, norm(0f, 1f, 0f), GOLD_LT, GOLD_LT, GOLD, GOLD);
        rodMesh = rod.build(GLES20.GL_TRIANGLES);

        weightMesh = buildOctahedron(0.055f, GOLD_LT, 0f, 0f, 0f).build(GLES20.GL_TRIANGLES);

        // Camera-facing soft shadows: a wide ambient halo and a tighter contact pool.
        haloMesh = buildShadowQuad(0f, 0.06f, -1.18f, 1.55f, 1.55f);
        contactShadowMesh = buildShadowQuad(0f, Y0 - 0.10f, -0.94f, 1.50f, 0.52f);
    }

    private static GLMesh buildShadowQuad(float cx, float cy, float z,
                                          float halfW, float halfH) {
        MeshBuilder mb = new MeshBuilder();
        float[] n = norm(0f, 0f, 1f);
        mb.v(pt(cx - halfW, cy - halfH, z), n, IVORY, 0f, 0f);
        mb.v(pt(cx + halfW, cy - halfH, z), n, IVORY, 1f, 0f);
        mb.v(pt(cx + halfW, cy + halfH, z), n, IVORY, 1f, 1f);
        mb.v(pt(cx - halfW, cy - halfH, z), n, IVORY, 0f, 0f);
        mb.v(pt(cx + halfW, cy + halfH, z), n, IVORY, 1f, 1f);
        mb.v(pt(cx - halfW, cy + halfH, z), n, IVORY, 0f, 1f);
        return mb.build(GLES20.GL_TRIANGLES);
    }

    /**
     * Builds one rounded side face. Horizontal rings are superellipses (soft
     * corners = rounded vertical arrises); their half-size tapers with height
     * and eases into a rounded apex. UVs follow the source triangle: across
     * the face u spans UV_L..UV_R, shrinking linearly toward the apex, v goes
     * from the image's bottom edge to its apex as height rises.
     */
    private GLMesh roundedFace(int face) {
        MeshBuilder mb = new MeshBuilder();
        for (int lvl = 0; lvl < LEVELS; lvl++) {
            float va = (float) lvl / LEVELS, vb = (float) (lvl + 1) / LEVELS;
            for (int k = 0; k < PER_FACE; k++) {
                float ua = (float) k / PER_FACE, ub = (float) (k + 1) / PER_FACE;
                float[] p00 = ringPoint(face, va, ua);
                float[] p10 = ringPoint(face, va, ub);
                float[] p11 = ringPoint(face, vb, ub);
                float[] p01 = ringPoint(face, vb, ua);
                float[] uv00 = faceUV(va, ua), uv10 = faceUV(va, ub);
                float[] uv11 = faceUV(vb, ub), uv01 = faceUV(vb, ua);
                if (face == 0) {
                    uv00[0] += FRONT_UV_SHIFT;
                    uv10[0] += FRONT_UV_SHIFT;
                    uv11[0] += FRONT_UV_SHIFT;
                    uv01[0] += FRONT_UV_SHIFT;
                }
                float[] n = faceNormal(face);

                mb.v(p00, n, IVORY, uv00[0], uv00[1]);
                mb.v(p10, n, IVORY, uv10[0], uv10[1]);
                mb.v(p11, n, IVORY, uv11[0], uv11[1]);
                mb.v(p00, n, IVORY, uv00[0], uv00[1]);
                mb.v(p11, n, IVORY, uv11[0], uv11[1]);
                mb.v(p01, n, IVORY, uv01[0], uv01[1]);
            }
        }
        return mb.build(GLES20.GL_TRIANGLES);
    }

    /**
     * Point on a rounded-square at height v for a given face and local u in
     * [0,1]. The face centre (u 0.25..0.75) is an exact straight line => the
     * picture stays perfectly flat (the clock does not warp); only the narrow
     * end bands u<0.25 / u>0.75 wrap a quarter-circle into the adjacent face,
     * so the four arrises roll smoothly. Half-size tapers linearly to TOP_SCALE
     * producing a small flat cap instead of a sharp apex.
     */
    private static float[] ringPoint(int face, float v, float u) {
        float scale = 1f + (TOP_SCALE - 1f) * v;   // linear taper, flat top
        float hx = HALF * scale;
        float hz = HALF * scale;
        float r = Math.min(hx, hz) * CORNER_R;

        // Build point in a canonical +Z-facing frame: local (lx, lz), then rotate.
        float lx, lz;
        float flat = hx - r;          // half-width of the straight middle segment
        if (u < 0.25f) {
            // Left corner arc: angle 180deg -> 90deg as u goes 0 -> 0.25.
            double a = Math.PI - (Math.PI * 0.5) * (u / 0.25f);
            lx = -flat + (float) (r * Math.cos(a));
            lz = (hx - r) + (float) (r * Math.sin(a));
        } else if (u > 0.75f) {
            // Right corner arc: angle 90deg -> 0deg as u goes 0.75 -> 1.
            double a = (Math.PI * 0.5) * (1f - (u - 0.75f) / 0.25f);
            lx = flat + (float) (r * Math.cos(a));
            lz = (hx - r) + (float) (r * Math.sin(a));
        } else {
            // Straight middle edge: exact plane.
            lx = -flat + 2f * flat * ((u - 0.25f) / 0.5f);
            lz = hx;
        }

        // Rotate canonical +Z frame into the requested face.
        float x, z;
        switch (face) {
            case 0: x = lx;  z = lz;  break;           // +Z
            case 1: x = lz;  z = -lx; break;           // +X
            case 2: x = -lx; z = -lz; break;           // -Z
            default: x = -lz; z = lx; break;           // -X
        }
        float y = Y0 + (Y1 - Y0) * v;
        return new float[]{x, y, z};
    }

    /** UV in the triangle image for height v and face-local u. */
    private static float[] faceUV(float v, float u) {
        float imgV = UV_BOT_V + (UV_TOP_V - UV_BOT_V) * v;
        float uL = UV_L + (0.5f - UV_L) * v;
        float uR = UV_R + (0.5f - UV_R) * v;
        float imgU = uL + (uR - uL) * u;
        return new float[]{imgU, imgV};
    }

    private static float[] faceNormal(int face) {
        switch (face) {
            case 0: return norm(0f, 0.6f, 1f);
            case 1: return norm(1f, 0.6f, 0f);
            case 2: return norm(0f, 0.6f, -1f);
            default: return norm(-1f, 0.6f, 0f);
        }
    }

    private static float zFront(float y) {
        float v = (y - Y0) / (Y1 - Y0);
        return HALF * (1f + (TOP_SCALE - 1f) * v);
    }

    private static MeshBuilder buildOctahedron(float r, float[] color, float ox, float oy, float oz) {
        MeshBuilder mb = new MeshBuilder();
        float[] px = pt(ox + r, oy, oz), nx = pt(ox - r, oy, oz);
        float[] py = pt(ox, oy + r, oz), ny = pt(ox, oy - r, oz);
        float[] pz = pt(ox, oy, oz + r), nz = pt(ox, oy, oz - r);
        int[][] tris = {{4, 0, 2}, {4, 2, 1}, {4, 1, 3}, {4, 3, 0},
                {5, 2, 0}, {5, 1, 2}, {5, 3, 1}, {5, 0, 3}};
        float[][] vs = {px, nx, py, ny, pz, nz};
        for (int[] tr : tris) {
            mb.tri(vs[tr[0]], vs[tr[1]], vs[tr[2]], norm(0.3f, 0.6f, 0.7f),
                    color, color, color);
        }
        return mb;
    }

    private static float[] pt(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    private static float[] norm(float[] v) {
        return norm(v[0], v[1], v[2]);
    }

    private static float[] norm(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-5f) return new float[]{0f, 0f, 1f};
        return new float[]{x / len, y / len, z / len};
    }

    private static float[] col(int argb) {
        return new float[]{((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f,
                (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f};
    }

    private static int buildProgram(String vsSrc, String fsSrc) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            throw new RuntimeException("Program link failed: " + log);
        }
        return p;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            GLES20.glDeleteShader(s);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }

    private static final class GLMesh {
        final int vbo;
        final int count;
        final int mode;

        GLMesh(float[] data, int mode) {
            ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder());
            FloatBuffer fb = bb.asFloatBuffer();
            fb.put(data);
            fb.position(0);
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            vbo = buffers[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            count = data.length / STRIDE_FLOATS;
            this.mode = mode;
        }
    }

    private static final class MeshBuilder {
        final ArrayList<Float> data = new ArrayList<Float>();

        void v(float[] p, float[] n, float[] c, float u, float t) {
            data.add(p[0]); data.add(p[1]); data.add(p[2]);
            data.add(n[0]); data.add(n[1]); data.add(n[2]);
            data.add(c[0]); data.add(c[1]); data.add(c[2]); data.add(c[3]);
            data.add(u); data.add(t);
        }

        void line(float[] a, float[] b, float[] c) {
            v(a, NORM_FRONT, c, 0f, 0f);
            v(b, NORM_FRONT, c, 0f, 0f);
        }

        void tri(float[] a, float[] b, float[] c, float[] n, float[] ca, float[] cb, float[] cc) {
            v(a, n, ca, 0f, 0f); v(b, n, cb, 0f, 0f); v(c, n, cc, 0f, 0f);
        }

        void quad(float[] a, float[] b, float[] c, float[] d, float[] n,
                  float[] ca, float[] cb, float[] cc, float[] cd) {
            tri(a, b, c, n, ca, cb, cc);
            tri(a, c, d, n, ca, cc, cd);
        }

        GLMesh build(int mode) {
            float[] arr = new float[data.size()];
            for (int i = 0; i < data.size(); i++) arr[i] = data.get(i);
            return new GLMesh(arr, mode);
        }
    }
}
