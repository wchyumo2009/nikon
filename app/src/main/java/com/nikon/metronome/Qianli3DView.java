package com.nikon.metronome;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * GLSurfaceView host for the true 3D "Thousand Li" metronome. Touch-drag
 * orbits the model; the render loop is driven on demand and self-sustains
 * while the metronome is running or settling.
 */
public class Qianli3DView extends GLSurfaceView {

    private final QianliRenderer renderer;

    public Qianli3DView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        renderer = new QianliRenderer();
        renderer.setResources(getResources());
        renderer.setHost(this);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public void attach(MetronomeEngine engine) {
        renderer.attach(engine);
    }

    public void setBpm(int bpm) {
        renderer.setBpm(bpm);
    }

    /** Kicks one frame so a state change is reflected even when idle. */
    public void onEngineStateChanged() {
        renderer.onEngineStateChanged();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return renderer.onTouch(event) || super.onTouchEvent(event);
    }
}
