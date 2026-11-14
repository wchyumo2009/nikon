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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends Activity implements MetronomeEngine.BeatListener {

    private static final String PREFS = "metronome_prefs";
    private static final int[] PRESETS = {60, 80, 100, 120, 160};
    private static final String[] SIGNATURES = {"2/4", "3/4", "4/4", "6/8"};
    private static final int[] BEATS = {2, 3, 4, 6};
    private static final String[] SOUND_NAMES = {"钢琴", "机械", "木鱼", "电子"};
    private static final MetronomeEngine.SoundType[] SOUND_TYPES = {
            MetronomeEngine.SoundType.PIANO,
            MetronomeEngine.SoundType.MECHANICAL,
            MetronomeEngine.SoundType.WOODBLOCK,
            MetronomeEngine.SoundType.ELECTRONIC
    };

    private MetronomeEngine engine;
    private PendulumView pendulumView;
    private TextView bpmValue;
    private TextView statusText;
    private SeekBar bpmSeek;
    private SeekBar volumeSeek;
    private Spinner timeSigSpinner;
    private Spinner soundSpinner;
    private CheckBox accentToggle;
    private Button startButton;
    private final Button[] modeButtons = new Button[3];
    private LinearLayout beatDots;
    private LinearLayout presetRow;
    private final ArrayList<View> dots = new ArrayList<>();
    private Vibrator vibrator;

    private int bpm = 120;
    private int beatsPerBar = 4;
    private int volume = 80;
    private boolean accent = true;
    private PendulumMode mode = PendulumMode.BOTTOM;
    private MetronomeEngine.SoundType soundType = MetronomeEngine.SoundType.PIANO;
    private boolean suppress;
    private boolean userSelecting;

    private final long[] taps = new long[5];
    private int tapCount;

    private static final int REQ_NOTIFICATIONS = 1;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Configuration cfg = getResources().getConfiguration();
        boolean wide = cfg.smallestScreenWidthDp >= 600
                || cfg.orientation == Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout container = findViewById(R.id.root_container);
        getLayoutInflater().inflate(wide ? R.layout.root_wide : R.layout.root_portrait,
                container, true);

        loadPrefs();
        engine = MetronomeService.getEngine(this);

        bindViews();
        buildPresets();
        bindActions();

        engine.setBpm(bpm);
        engine.setBeatsPerBar(beatsPerBar);
        engine.setVolume(volume / 100f);
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
        if (isFinishing()) {
            // Back really leaves the app; Home / screen off keep playing.
            MetronomeService.stopPlayback(this);
        }
        super.onDestroy();
    }

    private void bindViews() {
        pendulumView = findViewById(R.id.pendulum_view);
        bpmValue = findViewById(R.id.bpm_value);
        statusText = findViewById(R.id.status_text);
        bpmSeek = findViewById(R.id.bpm_seek);
        volumeSeek = findViewById(R.id.volume_seek);
        timeSigSpinner = findViewById(R.id.time_sig);
        soundSpinner = findViewById(R.id.sound_spinner);
        accentToggle = findViewById(R.id.accent_toggle);
        startButton = findViewById(R.id.start_button);
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
            button.setTextColor(getColor(R.color.sun_orange));
            button.setBackgroundResource(R.drawable.bg_button);
            button.setStateListAnimator(null);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(64), dp(40));
            params.setMargins(0, dp(4), dp(8), 0);
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

        bpmSeek.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!suppress) {
                    setBpm(MetronomeEngine.MIN_BPM + progress);
                }
            }
        });

        volumeSeek.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!suppress) {
                    volume = progress;
                    engine.setVolume(volume / 100f);
                    savePrefs();
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
                color = getColor(accent && accentBeat
                        ? R.color.dot_accent : R.color.dot_on);
            } else {
                color = getColor(R.color.dot_off);
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
        volumeSeek.setProgress(volume);
        accentToggle.setChecked(accent);
        timeSigSpinner.setSelection(indexOfBeat(beatsPerBar));
        soundSpinner.setSelection(soundType.ordinal());
        suppress = false;

        refreshModeButtons();
        rebuildDots();

        boolean running = engine.isRunning();
        startButton.setText(running ? R.string.stop : R.string.start);
        startButton.setBackgroundResource(running ? R.drawable.bg_stop : R.drawable.bg_start);
        startButton.setTextColor(getColor(running ? R.color.on_sun : R.color.ink));
        updateStatus();
        pendulumView.onEngineStateChanged();
    }

    private void refreshModeButtons() {
        PendulumMode[] values = PendulumMode.values();
        for (int i = 0; i < modeButtons.length; i++) {
            boolean selected = values[i] == mode;
            modeButtons[i].setSelected(selected);
            modeButtons[i].setTextColor(getColor(selected
                    ? R.color.ink : R.color.ink_soft));
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
            drawable.setColor(getColor(R.color.dot_off));
            drawable.setStroke(dp(1), getColor(R.color.outline));
            dot.setBackground(drawable);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(dp(5), 0, dp(5), 0);
            beatDots.addView(dot, params);
            dots.add(dot);
        }
    }

    private void updateStatus() {
        statusText.setText(engine.isRunning()
                ? getString(R.string.status_running, bpm, signatureOf(beatsPerBar))
                : getString(R.string.status_ready));
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
        volume = prefs.getInt("volume", 80);
        accent = prefs.getBoolean("accent", true);
        mode = PendulumMode.fromOrdinal(prefs.getInt("mode", PendulumMode.BOTTOM.ordinal()));
        soundType = MetronomeEngine.SoundType.fromOrdinal(
                prefs.getInt("sound", MetronomeEngine.SoundType.PIANO.ordinal()));
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("bpm", bpm)
                .putInt("beats", beatsPerBar)
                .putInt("volume", volume)
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
