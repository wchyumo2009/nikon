package com.nikon.metronome;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity implements MetronomeEngine.BeatListener {

    private static final String PREFS = "metronome_prefs";
    private static final int[] PRESETS = {60, 80, 100, 120, 160};
    private static final String[] SIGNATURES = {"2/4", "3/4", "4/4", "6/8"};
    private static final int[] BEATS = {2, 3, 4, 6};
    private static final String[] SOUND_NAMES = {
            "钢琴", "机械", "木鱼", "电子", "古琴", "编钟", "磬音", "堂鼓", "竹板"
    };
    private static final MetronomeEngine.SoundType[] SOUND_TYPES = {
            MetronomeEngine.SoundType.PIANO,
            MetronomeEngine.SoundType.MECHANICAL,
            MetronomeEngine.SoundType.WOODBLOCK,
            MetronomeEngine.SoundType.ELECTRONIC,
            MetronomeEngine.SoundType.GUQIN,
            MetronomeEngine.SoundType.BELL,
            MetronomeEngine.SoundType.CHIME,
            MetronomeEngine.SoundType.DRUM,
            MetronomeEngine.SoundType.BAMBOO
    };

    private MetronomeEngine engine;
    private PendulumView pendulumView;
    private TextView bpmValue;
    private TextView statusText;
    private SeekBar bpmSeek;
    private Spinner timeSigSpinner;
    private Spinner soundSpinner;
    private CheckBox accentToggle;
    private Button startButton;
    private Button themeToggle;
    private final Button[] modeButtons = new Button[3];
    private LinearLayout beatDots;
    private LinearLayout presetRow;
    private final ArrayList<View> dots = new ArrayList<>();
    private Vibrator vibrator;

    private int bpm = 120;
    private int beatsPerBar = 4;
    private boolean accent = true;
    private PendulumMode mode = PendulumMode.BOTTOM;
    private MetronomeEngine.SoundType soundType = MetronomeEngine.SoundType.PIANO;
    private boolean suppress;
    private boolean userSelecting;

    private final long[] taps = new long[5];
    private int tapCount;

    private static final int REQ_NOTIFICATIONS = 1;
    private static final String KEY_THEME = "theme_mode";
    private static final int THEME_FOLLOW = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    private static final int THEME_SONG = 3;
    private static final int THEME_TAIJI = 4;
    private static final int THEME_COUNT = 5;

    private int themeMode;
    private boolean themeSwitching;

    private int styleIndex = PendulumView.STYLE_SUNNY;
    private int accentColor = 0xFF8A3D;
    private FrameLayout rootContainer;
    private View pendulumCard;
    private GestureDetector styleGesture;
    private static final String KEY_STYLE = "style_index";
    private static final int[] STYLE_DISPLAY = {
            R.string.style_sunny, R.string.style_song, R.string.style_aurora,
            R.string.style_rose, R.string.style_neon, R.string.style_taiji
    };
    private static final int[][] STYLE_PAGE = {
            {0xFFF7E4, 0xFFEAF7FF, 0xFFE7FAF0},
            {0xFFF7F3E8, 0xFFEFECE0, 0xFFE6EFE9},
            {0xFFE9FBF6, 0xFFD7F3EF, 0xFFE1F4FB},
            {0xFFFFF1F6, 0xFFFFE4EC, 0xFFFFF5E8},
            {0xFF0B1020, 0xFF121A33, 0xFF0E1626},
            {0xFFF6F4EC, 0xFFEBEFE7, 0xFFDDE6DC}
    };
    private static final int[] STYLE_ACCENT = {
            0xFFFF8A3D, 0xFFA6452F, 0xFF12B5A6, 0xFFE5608A, 0xFF29E0E6,
            0xFF2F3B38
    };
    private static final int[] STYLE_BAR = {
            0xFFFFF1DC, 0xFFEFEADB, 0xFFD7F3EF, 0xFFFFE4EC, 0xFF0B1020,
            0xFFEDEFE8
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        themeMode = newBase.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_THEME, THEME_FOLLOW);
        super.attachBaseContext(newBase);
        if (themeMode == THEME_FOLLOW) {
            return;
        }
        Configuration override = new Configuration();
        // Song and Taiji palettes are both light surfaces (rice paper / ink wash).
        int night = themeMode == THEME_DARK
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        override.uiMode = (newBase.getResources().getConfiguration().uiMode
                & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        applyOverrideConfiguration(override);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (themeMode == THEME_SONG) {
            setTheme(R.style.SongTheme);
        } else if (themeMode == THEME_TAIJI) {
            setTheme(R.style.TaijiTheme);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Configuration cfg = getResources().getConfiguration();
        boolean wide = cfg.smallestScreenWidthDp >= 600
                || cfg.orientation == Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout container = findViewById(R.id.root_container);
        getLayoutInflater().inflate(wide ? R.layout.root_wide : R.layout.root_portrait,
                container, true);

        loadPrefs();
        applySystemBars();
        engine = MetronomeService.getEngine(this);

        bindViews();
        applyThemeChrome();
        buildPresets();
        bindActions();
        setupStyleGesture();
        applyStyleChrome();

        engine.setBpm(bpm);
        engine.setBeatsPerBar(beatsPerBar);
        engine.setAccentEnabled(accent);
        engine.setSoundType(soundType);
        pendulumView.attach(engine);
        pendulumView.setSwingMode(mode);
        pendulumView.setBpm(bpm);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        refreshUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine.setListener(this);
        IntentFilter filter = new IntentFilter(MetronomeService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        // The beat may have been stopped from the notification while hidden.
        refreshUi();
    }

    @Override
    protected void onStop() {
        // Keep the clock (and therefore the sound) running after the screen
        // turns off; only detach this Activity's UI callbacks.
        engine.setListener(null);
        unregisterReceiver(stateReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        engine.setListener(null);
        if (isFinishing() && !isChangingConfigurations() && !themeSwitching) {
            // Back really leaves the app; rotation, theme change, Home and screen off keep playing.
            MetronomeService.stopPlayback(this);
        }
        super.onDestroy();
    }

    private void bindViews() {
        pendulumView = findViewById(R.id.pendulum_view);
        rootContainer = findViewById(R.id.root_container);
        pendulumCard = findViewById(R.id.pendulum_card);
        bpmValue = findViewById(R.id.bpm_value);
        statusText = findViewById(R.id.status_text);
        bpmSeek = findViewById(R.id.bpm_seek);
        timeSigSpinner = findViewById(R.id.time_sig);
        soundSpinner = findViewById(R.id.sound_spinner);
        accentToggle = findViewById(R.id.accent_toggle);
        startButton = findViewById(R.id.start_button);
        themeToggle = findViewById(R.id.theme_toggle);
        beatDots = findViewById(R.id.beat_dots);
        presetRow = findViewById(R.id.preset_row);
        modeButtons[0] = findViewById(R.id.mode_top);
        modeButtons[1] = findViewById(R.id.mode_bottom);
        modeButtons[2] = findViewById(R.id.mode_spring);

        ArrayAdapter<String> signatures = new ArrayAdapter<>(this,
                R.layout.spinner_item, SIGNATURES);
        signatures.setDropDownViewResource(R.layout.spinner_dropdown_item);
        timeSigSpinner.setAdapter(signatures);

        ArrayAdapter<String> sounds = new ArrayAdapter<>(this,
                R.layout.spinner_item, SOUND_NAMES);
        sounds.setDropDownViewResource(R.layout.spinner_dropdown_item);
        soundSpinner.setAdapter(sounds);
    }

    private void buildPresets() {
        for (final int preset : PRESETS) {
            Button button = new Button(this);
            button.setText(String.valueOf(preset));
            button.setTextSize(13);
            button.setAllCaps(false);
            button.setTextColor(accentColor);
            button.setBackground(themeDrawable(R.attr.buttonBackground));
            button.setStateListAnimator(null);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(60), dp(36));
            params.setMargins(0, dp(4), dp(6), 0);
            button.setLayoutParams(params);
            button.setOnClickListener(v -> setBpm(preset));
            presetRow.addView(button);
        }
    }

    private void bindActions() {
        findViewById(R.id.bpm_minus).setOnClickListener(v -> setBpm(bpm - 1));
        findViewById(R.id.bpm_plus).setOnClickListener(v -> setBpm(bpm + 1));
        findViewById(R.id.start_button).setOnClickListener(v -> toggleMetronome());
        findViewById(R.id.tap_button).setOnClickListener(v -> onTap());
        themeToggle.setOnClickListener(v -> cycleTheme());

        // The thumb is only slightly larger than the track; grow it while a
        // finger is down so the touch target is easy to see and hit.
        View.OnTouchListener thumbGrow = (v, event) -> {
            if (!(v instanceof SeekBar)) {
                return false;
            }
            SeekBar bar = (SeekBar) v;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                bar.setThumb(themeDrawable(R.attr.seekThumbPressed));
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                bar.setThumb(themeDrawable(R.attr.seekThumb));
            }
            return false;
        };
        bpmSeek.setOnTouchListener(thumbGrow);

        bpmSeek.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!suppress) {
                    setBpm(MetronomeEngine.MIN_BPM + progress);
                }
            }
        });

        View.OnTouchListener touchMarker = (v, event) -> {
            userSelecting = true;
            return false;
        };
        View.OnKeyListener keyMarker = (v, keyCode, event) -> {
            userSelecting = true;
            return false;
        };
        timeSigSpinner.setOnTouchListener(touchMarker);
        timeSigSpinner.setOnKeyListener(keyMarker);
        soundSpinner.setOnTouchListener(touchMarker);
        soundSpinner.setOnKeyListener(keyMarker);

        timeSigSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppress || !userSelecting || position < 0 || position >= BEATS.length) {
                    return;
                }
                beatsPerBar = BEATS[position];
                engine.setBeatsPerBar(beatsPerBar);
                rebuildDots();
                updateStatus();
                savePrefs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        soundSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppress || !userSelecting || position < 0
                        || position >= SOUND_TYPES.length) {
                    return;
                }
                soundType = SOUND_TYPES[position];
                engine.setSoundType(soundType);
                savePrefs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        accentToggle.setOnCheckedChangeListener((button, checked) -> {
            if (suppress) {
                return;
            }
            accent = checked;
            engine.setAccentEnabled(accent);
            savePrefs();
        });

        for (int i = 0; i < modeButtons.length; i++) {
            final PendulumMode target = PendulumMode.values()[i];
            modeButtons[i].setOnClickListener(v -> setMode(target));
        }
    }

    private void setMode(PendulumMode target) {
        mode = target;
        pendulumView.setSwingMode(mode);
        refreshModeButtons();
        savePrefs();
    }

    private void setBpm(int value) {
        bpm = Math.max(MetronomeEngine.MIN_BPM, Math.min(MetronomeEngine.MAX_BPM, value));
        engine.setBpm(bpm);
        pendulumView.setBpm(bpm);
        suppress = true;
        bpmValue.setText(String.valueOf(bpm));
        bpmSeek.setProgress(bpm - MetronomeEngine.MIN_BPM);
        suppress = false;
        updateStatus();
        savePrefs();
    }

    private void toggleMetronome() {
        if (engine.isRunning()) {
            MetronomeService.stopPlayback(this);
        } else {
            ensureNotificationPermission();
            MetronomeService.startPlayback(this);
        }
        refreshUi();
    }

    private void cycleTheme() {
        int leavingStyle = (themeMode == THEME_SONG) ? PendulumView.STYLE_SONG
                : (themeMode == THEME_TAIJI) ? PendulumView.STYLE_TAIJI : -1;
        themeMode = (themeMode + 1) % THEME_COUNT;
        if (leavingStyle >= 0 && styleIndex == leavingStyle) {
            styleIndex = PendulumView.STYLE_SUNNY;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(KEY_THEME, themeMode).putInt(KEY_STYLE, styleIndex).apply();
        themeSwitching = true;
        recreate();
    }

    private String themeLabel() {
        if (themeMode == THEME_LIGHT) {
            return getString(R.string.theme_light);
        }
        if (themeMode == THEME_DARK) {
            return getString(R.string.theme_dark);
        }
        if (themeMode == THEME_SONG) {
            return getString(R.string.theme_song);
        }
        if (themeMode == THEME_TAIJI) {
            return getString(R.string.theme_taiji);
        }
        return getString(R.string.theme_follow);
    }

    private boolean isSongTheme() {
        return themeMode == THEME_SONG;
    }

    private boolean isTaijiTheme() {
        return themeMode == THEME_TAIJI;
    }

    private boolean isNightMode() {
        if (themeMode == THEME_DARK) {
            return true;
        }
        if (themeMode != THEME_FOLLOW) {
            return false;
        }
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySystemBars() {
        int bar = themeColor(R.attr.barColor);
        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bar);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags = isNightMode()
                ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                : flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(flags);
    }

    /** Resolves a theme attribute so dynamically created views follow the palette. */
    private int themeColor(int attr) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attr, value, true)) {
            return 0;
        }
        return value.resourceId != 0 ? getColor(value.resourceId) : value.data;
    }

    private Drawable themeDrawable(int attr) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attr, value, true) || value.resourceId == 0) {
            return null;
        }
        return getDrawable(value.resourceId);
    }

    private void applyThemeChrome() {
        applyStyleChrome();
        TextView title = findViewById(R.id.title);
        if (title != null) {
            title.setText(isSongTheme() ? R.string.app_name_song
                    : isTaijiTheme() ? R.string.app_name_taiji : R.string.app_name);
        }
        TextView subtitle = findViewById(R.id.subtitle);
        if (subtitle != null) {
            subtitle.setText(isSongTheme() ? R.string.subtitle_song
                    : isTaijiTheme() ? R.string.subtitle_taiji : R.string.subtitle);
        }
    }

    /** Left/right swipe on the pendulum switches the metronome style. */
    private void setupStyleGesture() {
        styleGesture = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                            float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) {
                            return false;
                        }
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > dp(60)) {
                            cycleStyle(dx < 0 ? 1 : -1);
                            return true;
                        }
                        return false;
                    }
                });
        pendulumCard.setOnTouchListener((v, e) -> {
            styleGesture.onTouchEvent(e);
            return true;
        });
    }

    /**
     * Left/right swipe cycles through ALL six styles, including Song and Taiji.
     * Those two need dedicated resource themes, so entering or leaving them
     * recreates the activity; the other styles swap live.
     */
    private void cycleStyle(int dir) {
        int[] order = {PendulumView.STYLE_SUNNY, PendulumView.STYLE_SONG,
                PendulumView.STYLE_AURORA, PendulumView.STYLE_ROSE,
                PendulumView.STYLE_NEON, PendulumView.STYLE_TAIJI};
        int current = (themeMode == THEME_SONG) ? PendulumView.STYLE_SONG
                : (themeMode == THEME_TAIJI) ? PendulumView.STYLE_TAIJI : styleIndex;
        int idx = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                idx = i;
                break;
            }
        }
        int next = order[(idx + dir + order.length) % order.length];
        int nextTheme = next == PendulumView.STYLE_SONG ? THEME_SONG
                : next == PendulumView.STYLE_TAIJI ? THEME_TAIJI
                : THEME_FOLLOW;
        boolean themeChanged = themeMode != nextTheme
                && ((themeMode == THEME_SONG || themeMode == THEME_TAIJI)
                || nextTheme != THEME_FOLLOW);
        styleIndex = next;
        if (themeChanged) {
            themeMode = nextTheme;
            SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
            editor.putInt(KEY_STYLE, styleIndex).putInt(KEY_THEME, themeMode).apply();
            Toast.makeText(getApplicationContext(),
                    getString(STYLE_DISPLAY[next]), Toast.LENGTH_SHORT).show();
            themeSwitching = true;
            recreate();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(KEY_STYLE, styleIndex).apply();
        applyStyleChrome();
        Toast.makeText(this, getString(STYLE_DISPLAY[next]),
                Toast.LENGTH_SHORT).show();
    }

    /** Applies the active style to the pendulum, page background and accents. */
    private void applyStyleChrome() {
        int eff = (themeMode == THEME_SONG) ? PendulumView.STYLE_SONG
                : (themeMode == THEME_TAIJI) ? PendulumView.STYLE_TAIJI : styleIndex;
        pendulumView.setStyle(eff);
        // Taiji only rotates the disc, so the swing-mode choices do not apply.
        boolean modesDisabled = eff == PendulumView.STYLE_TAIJI;
        for (Button modeButton : modeButtons) {
            modeButton.setEnabled(!modesDisabled);
            modeButton.setAlpha(modesDisabled ? 0.45f : 1f);
        }
        boolean night = isNightMode();
        applyPageBackground(eff, night);
        int bar = night ? 0x14202F : STYLE_BAR[eff];
        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bar);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        boolean darkPage = (eff == PendulumView.STYLE_NEON) || night;
        flags = darkPage
                ? (flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                : (flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        decor.setSystemUiVisibility(flags);
        accentColor = STYLE_ACCENT[eff];
        boolean inkPage = eff == PendulumView.STYLE_TAIJI;
        TextView title = findViewById(R.id.title);
        TextView subtitle = findViewById(R.id.subtitle);
        if (title != null) {
            title.setTextColor(darkPage ? 0xFFFFFFFF
                    : inkPage ? 0xFF232E2A : themeColor(R.attr.textPrimary));
        }
        if (subtitle != null) {
            subtitle.setTextColor(darkPage ? 0xCCFFFFFF
                    : inkPage ? 0xFF6B7C71 : themeColor(R.attr.textSecondary));
        }
        rebuildDots();
        refreshPresetColors();
    }

    private void applyPageBackground(int style, boolean night) {
        if (night) {
            rootContainer.setBackground(themeDrawable(R.attr.appBackground));
            return;
        }
        int[] g = STYLE_PAGE[style];
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{g[0], g[1], g[2]});
        rootContainer.setBackground(d);
    }

    private void refreshPresetColors() {
        for (int i = 0; i < presetRow.getChildCount(); i++) {
            View child = presetRow.getChildAt(i);
            if (child instanceof Button) {
                ((Button) child).setTextColor(accentColor);
            }
        }
    }

    private static int darkenColor(int color, float f) {
        int r = (int) (((color >> 16) & 0xFF) * f);
        int g = (int) (((color >> 8) & 0xFF) * f);
        int b = (int) ((color & 0xFF) * f);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private void updateKeepScreenOn() {
        if (engine.isRunning()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS);
        }
    }

    private void onTap() {
        long now = SystemClock.uptimeMillis();
        if (tapCount > 0 && now - taps[tapCount - 1] > 2000) {
            tapCount = 0;
        }
        if (tapCount == taps.length) {
            System.arraycopy(taps, 1, taps, 0, taps.length - 1);
            tapCount--;
        }
        taps[tapCount++] = now;
        if (tapCount < 2) {
            return;
        }
        long sum = 0;
        int samples = 0;
        for (int i = 1; i < tapCount; i++) {
            long delta = taps[i] - taps[i - 1];
            if (delta >= 150 && delta <= 2000) {
                sum += delta;
                samples++;
            }
        }
        if (samples > 0) {
            setBpm((int) Math.round(60_000.0 * samples / sum));
        }
    }

    @Override
    public void onBeat(int beatInBar, boolean accentBeat) {
        for (int i = 0; i < dots.size(); i++) {
            View dot = dots.get(i);
            GradientDrawable drawable = (GradientDrawable) dot.getBackground();
            int color;
            if (i == beatInBar) {
                color = (accent && accentBeat)
                        ? darkenColor(accentColor, 0.78f) : accentColor;
            } else {
                color = themeColor(R.attr.dotOff);
            }
            drawable.setColor(color);
            dot.invalidate();
        }
        if (accent && accentBeat) {
            vibrateAccent();
        }
    }

    private void vibrateAccent() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(
                    18, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(18);
        }
    }

    private void refreshUi() {
        suppress = true;
        bpmValue.setText(String.valueOf(bpm));
        bpmSeek.setProgress(bpm - MetronomeEngine.MIN_BPM);
        accentToggle.setChecked(accent);
        timeSigSpinner.setSelection(indexOfBeat(beatsPerBar));
        soundSpinner.setSelection(soundType.ordinal());
        suppress = false;

        refreshModeButtons();
        rebuildDots();

        boolean running = engine.isRunning();
        startButton.setText(running ? R.string.stop : R.string.start);
        startButton.setBackground(themeDrawable(
                running ? R.attr.stopBackground : R.attr.startBackground));
        startButton.setTextColor(themeColor(R.attr.textAction));
        themeToggle.setText(themeLabel());
        updateKeepScreenOn();
        updateStatus();
        pendulumView.onEngineStateChanged();
    }

    private void refreshModeButtons() {
        PendulumMode[] values = PendulumMode.values();
        for (int i = 0; i < modeButtons.length; i++) {
            boolean selected = values[i] == mode;
            modeButtons[i].setSelected(selected);
            modeButtons[i].setTextColor(themeColor(R.attr.textPrimary));
        }
    }

    private void rebuildDots() {
        beatDots.removeAllViews();
        dots.clear();
        int size = dp(14);
        for (int i = 0; i < beatsPerBar; i++) {
            View dot = new View(this);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(themeColor(R.attr.dotOff));
            drawable.setStroke(dp(1), themeColor(R.attr.dotStroke));
            dot.setBackground(drawable);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(dp(5), 0, dp(5), 0);
            beatDots.addView(dot, params);
            dots.add(dot);
        }
    }

    private void updateStatus() {
        boolean song = isSongTheme();
        boolean taiji = isTaijiTheme();
        if (engine.isRunning()) {
            int res = song ? R.string.status_running_song
                    : taiji ? R.string.status_running_taiji : R.string.status_running;
            statusText.setText(getString(res, bpm, signatureOf(beatsPerBar)));
        } else {
            int res = song ? R.string.status_ready_song
                    : taiji ? R.string.status_ready_taiji : R.string.status_ready;
            statusText.setText(getString(res));
        }
    }

    private static int indexOfBeat(int beats) {
        for (int i = 0; i < BEATS.length; i++) {
            if (BEATS[i] == beats) {
                return i;
            }
        }
        return 2;
    }

    private static String signatureOf(int beats) {
        int index = indexOfBeat(beats);
        return SIGNATURES[index];
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        bpm = prefs.getInt("bpm", 120);
        beatsPerBar = prefs.getInt("beats", 4);
        accent = prefs.getBoolean("accent", true);
        mode = PendulumMode.fromOrdinal(prefs.getInt("mode", PendulumMode.BOTTOM.ordinal()));
        soundType = MetronomeEngine.SoundType.fromOrdinal(
                prefs.getInt("sound", MetronomeEngine.SoundType.PIANO.ordinal()));
        themeMode = prefs.getInt(KEY_THEME, THEME_FOLLOW);
        styleIndex = prefs.getInt(KEY_STYLE, PendulumView.STYLE_SUNNY);
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("bpm", bpm)
                .putInt("beats", beatsPerBar)
                .putBoolean("accent", accent)
                .putInt("mode", mode.ordinal())
                .putInt("sound", soundType.ordinal())
                .apply();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static abstract class SimpleSeekBarListener
            implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
