package com.nikon.metronome;

/**
 * Generates the click samples in memory. No audio assets are shipped, so the
 * APK stays small and every sound is reproducible on any Android device.
 */
final class SoundSynth {

    private SoundSynth() {
    }

    static short[] render(MetronomeEngine.SoundType type, boolean accent, int sampleRate) {
        int durationMs;
        switch (type) {
            case PIANO:
                durationMs = 460;
                break;
            case BELL:
                durationMs = 720;
                break;
            case GUQIN:
                durationMs = 420;
                break;
            case CHIME:
                durationMs = 340;
                break;
            case DRUM:
                durationMs = 230;
                break;
            case WOODBLOCK:
                durationMs = 170;
                break;
            case BAMBOO:
                durationMs = 110;
                break;
            case TAIJI:
                durationMs = 460;
                break;
            case ELECTRONIC:
                durationMs = 90;
                break;
            case MECHANICAL:
            default:
                durationMs = 120;
                break;
        }

        int frames = (int) (sampleRate * durationMs / 1000.0);
        short[] pcm = new short[frames];
        double base = accent ? 1.18 : 1.0;

        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double wave;

            switch (type) {
                case PIANO: {
                    double f = (accent ? 880.0 : 659.25);
                    double attack = 1.0 - Math.exp(-t * 900.0);
                    double decay = Math.exp(-t * 5.0);
                    wave = Math.sin(2 * Math.PI * f * t)
                            + 0.46 * Math.sin(2 * Math.PI * f * 2.003 * t) * Math.exp(-t * 5.8)
                            + 0.23 * Math.sin(2 * Math.PI * f * 3.011 * t) * Math.exp(-t * 7.5)
                            + 0.11 * Math.sin(2 * Math.PI * f * 4.021 * t) * Math.exp(-t * 9.5)
                            + 0.05 * Math.sin(2 * Math.PI * f * 5.032 * t) * Math.exp(-t * 12.0);
                    wave += noise(i) * Math.exp(-t * 130.0) * 0.14;
                    wave *= attack * decay * 0.42 * base;
                    break;
                }
                case WOODBLOCK: {
                    double f = accent ? 800.0 : 620.0;
                    double decay = Math.exp(-t * 30.0);
                    wave = (Math.sin(2 * Math.PI * f * t)
                            + 0.32 * Math.sin(2 * Math.PI * f * 2.42 * t)
                            + noise(i) * 0.18 * Math.exp(-t * 160.0)) * decay * 0.62 * base;
                    break;
                }
                case ELECTRONIC: {
                    double f = accent ? 1320.0 : 990.0;
                    double s = Math.sin(2 * Math.PI * f * t);
                    wave = (s * 0.32 + Math.signum(s) * 0.24)
                            * Math.exp(-t * 75.0) * 0.6 * base;
                    break;
                }
                // Plucked silk string: low fundamentals, slowly fading partials.
                case GUQIN: {
                    double f = accent ? 293.66 : 220.0;
                    double attack = 1.0 - Math.exp(-t * 700.0);
                    double decay = Math.exp(-t * 4.2);
                    wave = Math.sin(2 * Math.PI * f * t)
                            + 0.52 * Math.sin(2 * Math.PI * f * 2.002 * t) * Math.exp(-t * 3.4)
                            + 0.28 * Math.sin(2 * Math.PI * f * 3.008 * t) * Math.exp(-t * 5.1)
                            + 0.14 * Math.sin(2 * Math.PI * f * 4.016 * t) * Math.exp(-t * 7.2);
                    wave += noise(i) * Math.exp(-t * 220.0) * 0.10;
                    wave *= attack * decay * 0.46 * base;
                    break;
                }
                // Bronze bell: deliberately inharmonic partials with a slow bloom.
                case BELL: {
                    double f = accent ? 587.33 : 440.0;
                    double attack = 1.0 - Math.exp(-t * 260.0);
                    wave = Math.sin(2 * Math.PI * f * 0.56 * t) * Math.exp(-t * 2.1)
                            + 0.72 * Math.sin(2 * Math.PI * f * 1.0 * t) * Math.exp(-t * 2.6)
                            + 0.48 * Math.sin(2 * Math.PI * f * 1.47 * t) * Math.exp(-t * 3.4)
                            + 0.34 * Math.sin(2 * Math.PI * f * 2.76 * t) * Math.exp(-t * 5.0)
                            + 0.20 * Math.sin(2 * Math.PI * f * 5.41 * t) * Math.exp(-t * 8.0)
                            + 0.11 * Math.sin(2 * Math.PI * f * 8.93 * t) * Math.exp(-t * 12.0);
                    wave *= attack * 0.34 * base;
                    break;
                }
                // Stone chime: bright, pure and quick.
                case CHIME: {
                    double f = accent ? 1760.0 : 1318.51;
                    double attack = 1.0 - Math.exp(-t * 1200.0);
                    wave = (Math.sin(2 * Math.PI * f * t)
                            + 0.44 * Math.sin(2 * Math.PI * f * 2.76 * t) * Math.exp(-t * 8.0)
                            + 0.22 * Math.sin(2 * Math.PI * f * 5.18 * t) * Math.exp(-t * 14.0))
                            * attack * Math.exp(-t * 11.0) * 0.52 * base;
                    break;
                }
                // Hall drum: falling low sine plus a short skin-noise transient.
                case DRUM: {
                    double f = 150.0 - 90.0 * (1.0 - Math.exp(-t * 24.0));
                    double phase = 2 * Math.PI * (150.0 * t
                            - 90.0 * (t - (1.0 - Math.exp(-t * 24.0)) / 24.0));
                    wave = Math.sin(phase) * Math.exp(-t * 15.0) * 0.72
                            + noise(i) * Math.exp(-t * 90.0) * 0.34
                            + Math.sin(2 * Math.PI * f * 2.0 * t) * Math.exp(-t * 26.0) * 0.16;
                    wave *= base;
                    break;
                }
                // Bamboo clapper: dry, hollow and very short.
                case BAMBOO: {
                    double f = accent ? 940.0 : 700.0;
                    double decay = Math.exp(-t * 46.0);
                    wave = (Math.sin(2 * Math.PI * f * t)
                            + 0.26 * Math.sin(2 * Math.PI * f * 1.86 * t)
                            + Math.signum(Math.sin(2 * Math.PI * f * 0.5 * t)) * 0.12
                            + noise(i) * 0.42 * Math.exp(-t * 190.0)) * decay * 0.55 * base;
                    break;
                }
                // Taiji: yin-yang resonance. A low rounded fundamental (yin)
                // meets a clear fifth and octave shimmer (yang), with a soft
                // breath transient instead of a hard percussive attack.
                case TAIJI: {
                    double f = accent ? 261.63 : 220.0;
                    double attack = 1.0 - Math.exp(-t * 420.0);
                    double decay = Math.exp(-t * 3.2);
                    wave = Math.sin(2 * Math.PI * f * t)
                            + 0.42 * Math.sin(2 * Math.PI * f * 1.5 * t) * Math.exp(-t * 3.8)
                            + 0.26 * Math.sin(2 * Math.PI * f * 2.0 * t) * Math.exp(-t * 5.4)
                            + 0.12 * Math.sin(2 * Math.PI * f * 3.01 * t) * Math.exp(-t * 7.6)
                            + 0.18 * Math.sin(2 * Math.PI * f * 0.5 * t) * Math.exp(-t * 4.0);
                    wave += noise(i) * Math.exp(-t * 140.0) * 0.05;
                    wave *= attack * decay * 0.46 * base;
                    break;
                }
                case MECHANICAL:
                default: {
                    double f = accent ? 1650.0 : 1180.0;
                    double decay = Math.exp(-t * 52.0);
                    wave = (Math.sin(2 * Math.PI * f * t)
                            + 0.38 * Math.sin(2 * Math.PI * f * 2.71 * t)
                            + noise(i) * 0.3 * Math.exp(-t * 200.0)) * decay * 0.55 * base;
                    break;
                }
            }

            if (wave > 1.0) {
                wave = 1.0;
            } else if (wave < -1.0) {
                wave = -1.0;
            }
            pcm[i] = (short) (wave * Short.MAX_VALUE * 0.92);
        }
        normalise(pcm);
        return pcm;
    }

    /**
     * Timbres have very different raw peaks. Scale every sample to the same
     * near-full-scale peak so the volume slider means the same for all of them.
     */
    private static void normalise(short[] pcm) {
        int peak = 0;
        for (short value : pcm) {
            int magnitude = Math.abs(value);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        int target = (int) (Short.MAX_VALUE * 0.94);
        if (peak == 0 || peak >= target) {
            return;
        }
        double gain = Math.min((double) target / peak, 4.0);
        for (int i = 0; i < pcm.length; i++) {
            int amplified = (int) Math.round(pcm[i] * gain);
            pcm[i] = (short) Math.max(-target, Math.min(target, amplified));
        }
    }

    private static double noise(int i) {
        double v = Math.sin(i * 12.9898) * 43758.5453;
        return (v - Math.floor(v)) * 2.0 - 1.0;
    }
}
