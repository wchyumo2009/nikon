package com.nikon.metronome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

/**
 * Wall-clock driven metronome. A dedicated thread schedules beats against
 * System.nanoTime() so that UI redraws never shift the tempo. Static
 * AudioTracks keep click latency low and allow piano notes to overlap.
 */
public final class MetronomeEngine {

    private static final String TAG = "MetronomeEngine";

    public enum SoundType {
        PIANO, MECHANICAL, WOODBLOCK, ELECTRONIC,
        GUQIN, BELL, CHIME, DRUM, BAMBOO, TAIJI;

        public static SoundType fromOrdinal(int ordinal) {
            SoundType[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return PIANO;
            }
            return values[ordinal];
        }
    }

    public interface BeatListener {
        void onBeat(int beatInBar, boolean accent);
    }

    public static final int MIN_BPM = 40;
    public static final int MAX_BPM = 240;
    private static final int SAMPLE_RATE = 44100;
    private static final int STREAMS = 4;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object clockLock = new Object();
    private final Object tracksLock = new Object();
    private final AudioManager audioManager;

    private AudioTrack[] accentTracks = new AudioTrack[STREAMS];
    private AudioTrack[] normalTracks = new AudioTrack[STREAMS];
    private final boolean[] accentPlayed = new boolean[STREAMS];
    private final boolean[] normalPlayed = new boolean[STREAMS];
    private int playCursor;
    private SoundType soundType = SoundType.PIANO;

    private volatile boolean running;
    private volatile int bpm = 120;
    private volatile int beatsPerBar = 4;
    private volatile float volume = 0.8f;
    private volatile boolean accentEnabled = true;
    private volatile BeatListener listener;
    private volatile Thread worker;

    /** Time origin and beat index at that origin; used for both clock and animation. */
    private long anchorNanos;
    private double anchorBeat;
    private long periodNanos;
    private volatile long nextBeatNanos;
    private long beatCounter;

    public MetronomeEngine(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        periodNanos = 60_000_000_000L / bpm;
        anchorNanos = System.nanoTime();
        rebuildTracks(soundType);
    }

    public boolean isRunning() {
        return running;
    }

    public int getBpm() {
        return bpm;
    }

    public void setListener(BeatListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (running) {
            return;
        }
        synchronized (tracksLock) {
            ensureTracks();
        }
        if (audioManager != null
                && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Log.w(TAG, "STREAM_MUSIC volume is 0; clicks will be inaudible");
        }
        running = true;
        synchronized (clockLock) {
            long now = System.nanoTime();
            periodNanos = 60_000_000_000L / bpm;
            anchorNanos = now;
            anchorBeat = 0;
            nextBeatNanos = now;
            beatCounter = 0;
        }
        worker = new Thread(this::clockLoop, "metronome-clock");
        worker.start();
    }

    public void stop() {
        running = false;
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
        worker = null;
        // Static tracks keep reporting "started" after the last buffer has
        // played; stop them so the app no longer holds an active audio stream.
        synchronized (tracksLock) {
            silenceTracks();
        }
    }

    private void silenceTracks() {
        for (int i = 0; i < STREAMS; i++) {
            if (normalPlayed[i]) {
                stopTrack(normalTracks[i]);
            }
            if (accentPlayed[i]) {
                stopTrack(accentTracks[i]);
            }
        }
    }

    private static void stopTrack(AudioTrack track) {
        if (!isUsable(track)) {
            return;
        }
        try {
            if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                track.stop();
            }
        } catch (IllegalStateException ignored) {
            // The audio device was already torn down.
        }
    }

    /** Continuous beat counter used by the pendulum to draw the exact phase. */
    public double getBeatPhase() {
        synchronized (clockLock) {
            return anchorBeat + (System.nanoTime() - anchorNanos) / (double) periodNanos;
        }
    }

    public void setBpm(int value) {
        int next = Math.max(MIN_BPM, Math.min(MAX_BPM, value));
        synchronized (clockLock) {
            double phaseNow = anchorBeat + (System.nanoTime() - anchorNanos) / (double) periodNanos;
            bpm = next;
            periodNanos = 60_000_000_000L / next;
            anchorNanos = System.nanoTime();
            anchorBeat = phaseNow;
            if (running) {
                double nextBeat = Math.ceil(phaseNow + 1e-6);
                nextBeatNanos = anchorNanos + (long) ((nextBeat - phaseNow) * periodNanos);
            }
        }
    }

    public void setBeatsPerBar(int beats) {
        beatsPerBar = Math.max(1, Math.min(12, beats));
    }

    public void setAccentEnabled(boolean enabled) {
        accentEnabled = enabled;
    }

    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
    }

    public void setSoundType(SoundType type) {
        if (type == null || type == soundType) {
            return;
        }
        synchronized (tracksLock) {
            soundType = type;
            rebuildTracks(type);
        }
    }

    public SoundType getSoundType() {
        return soundType;
    }

    /** Only called when the Activity is really finishing, not on a rotation. */
    public void release() {
        stop();
        synchronized (tracksLock) {
            releaseTracks();
        }
    }

    private void clockLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        while (running && Thread.currentThread() == worker) {
            long now = System.nanoTime();
            long wait = nextBeatNanos - now;
            if (wait > 1_000_000L) {
                LockSupport.parkNanos(wait - 700_000L);
                continue;
            }
            while (running && (now = System.nanoTime()) < nextBeatNanos) {
                LockSupport.parkNanos(50_000L);
            }
            if (!running) {
                break;
            }

            fireBeat();

            synchronized (clockLock) {
                beatCounter++;
                nextBeatNanos += periodNanos;
                if (nextBeatNanos - System.nanoTime() < -periodNanos) {
                    // The device was suspended for more than one beat: resynchronise.
                    long fresh = System.nanoTime();
                    anchorNanos = fresh;
                    anchorBeat = beatCounter;
                    nextBeatNanos = fresh + periodNanos;
                }
            }
        }
    }

    private void fireBeat() {
        int beatInBar = (int) (beatCounter % beatsPerBar);
        boolean accent = accentEnabled && beatInBar == 0;
        playClick(accent);
        // While the screen is off the Activity detaches itself; skip the main
        // thread work entirely so only the audio clock keeps running.
        BeatListener current = listener;
        if (current == null || !running) {
            return;
        }
        mainHandler.post(() -> {
            if (listener != null && running) {
                current.onBeat(beatInBar, accent);
            }
        });
    }

    private void playClick(boolean accent) {
        synchronized (tracksLock) {
            ensureTracks();
            int slot = playCursor % STREAMS;
            playCursor++;
            AudioTrack[] tracks = accent ? accentTracks : normalTracks;
            boolean[] played = accent ? accentPlayed : normalPlayed;
            AudioTrack track = tracks[slot];
            if (!isUsable(track)) {
                Log.e(TAG, "no initialized AudioTrack for click");
                return;
            }
            try {
                // Stop first so a repeated play() restarts the static buffer from
                // the beginning. The PCM is written once at build time and never
                // changes, so it must NOT be reloaded: reloadStaticData() causes a
                // native crash on some Android 14+ devices, killing the app after
                // the first few beats.
                if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                    track.stop();
                }
                track.setVolume(volume * (accent ? 1f : 0.85f));
                track.play();
                played[slot] = true;
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.w(TAG, "AudioTrack did not enter PLAYING state");
                }
            } catch (IllegalStateException e) {
                // The audio device can disappear at any time; rebuild so the
                // next beat has a usable track instead of staying silent.
                Log.e(TAG, "audio device lost while playing click", e);
                rebuildTracks(soundType);
            }
        }
    }

    /**
     * Static buffers can be released (release()) or fail to initialise on some
     * devices. Rebuild them on demand so the metronome never ticks silently.
     * Callers must hold tracksLock.
     */
    private void ensureTracks() {
        for (int i = 0; i < STREAMS; i++) {
            if (!isUsable(normalTracks[i]) || !isUsable(accentTracks[i])) {
                Log.w(TAG, "static AudioTracks missing; rebuilding " + soundType);
                rebuildTracks(soundType);
                return;
            }
        }
    }

    private static boolean isUsable(AudioTrack track) {
        return track != null && track.getState() == AudioTrack.STATE_INITIALIZED;
    }

    private void rebuildTracks(SoundType type) {
        releaseTracks();
        Arrays.fill(normalPlayed, false);
        Arrays.fill(accentPlayed, false);
        short[] normal = SoundSynth.render(type, false, SAMPLE_RATE);
        short[] accent = SoundSynth.render(type, true, SAMPLE_RATE);
        for (int i = 0; i < STREAMS; i++) {
            normalTracks[i] = createTrack(normal);
            accentTracks[i] = createTrack(accent);
        }
    }

    private AudioTrack createTrack(short[] pcm) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        try {
            AudioTrack track = new AudioTrack(attributes, format, pcm.length * 2,
                    AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            // A MODE_STATIC track reports STATE_NO_STATIC_DATA until its buffer
            // is filled; only after write() does it become STATE_INITIALIZED.
            int state = track.getState();
            if (state != AudioTrack.STATE_NO_STATIC_DATA
                    && state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack failed to initialise (state=" + state + ")");
                track.release();
                return null;
            }
            int written = track.write(pcm, 0, pcm.length);
            if (written != pcm.length
                    || track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "static buffer write failed: " + written + "/" + pcm.length
                        + " state=" + track.getState());
                track.release();
                return null;
            }
            return track;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // No audio output available (e.g. emulator without audio HAL).
            Log.e(TAG, "cannot create AudioTrack", e);
        }
        return null;
    }

    private void releaseTracks() {
        releaseArray(accentTracks);
        releaseArray(normalTracks);
    }

    private static void releaseArray(AudioTrack[] tracks) {
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i] != null) {
                try {
                    tracks[i].release();
                } catch (RuntimeException ignored) {
                    // Already released by the system.
                }
                tracks[i] = null;
            }
        }
    }
}
